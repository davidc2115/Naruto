// Pont JNI entre l'app Kotlin/Compose et llama.cpp.
//
// Choix de conception notables :
//  - Tout le texte (prompt en entrée, morceaux de token en sortie) transite en
//    octets UTF-8 bruts (jbyteArray), jamais via jstring/NewStringUTF ou
//    GetStringUTFChars : ces API JNI utilisent l'UTF-8 "modifié" de Java et
//    corrompent silencieusement les fragments de token multi-octets que
//    produit un tokenizer BPE (un caractère accentué ou un emoji peut être
//    coupé au milieu entre deux tokens). Le décodage UTF-8 incrémental
//    correct est fait côté Kotlin (voir engine/Utf8StreamDecoder.kt).
//  - Chaque "ModelSession" est un contexte llama.cpp autonome ; l'historique
//    de conversation est reconstruit et retokenisé à chaque tour par
//    PromptBuilder.kt côté Kotlin plutôt que de tenter un cache incrémental
//    complexe entre tours — plus simple et robuste, largement compensé par
//    la vitesse de prompt-processing de llama.cpp sur mobile.
//  - nativeGenerate() est un appel bloquant, pensé pour être invoqué depuis
//    un thread de coroutine Kotlin (Dispatchers.Default). L'annulation
//    (bouton "Stop", changement d'écran) passe par nativeAbortGeneration(),
//    qui positionne un drapeau atomique vérifié à chaque token généré.
//  - Toute exception C++ levée pendant la génération (le pilote Vulkan de
//    certains GPU Android peut lever vk::DeviceLostError en cas de crash
//    GPU, voir docs/VULKAN_NOTES.md) est interceptée ici : on ne laisse
//    jamais une exception traverser la frontière JNI, ce qui abattrait tout
//    le processus de l'application. InferenceEngine.kt reçoit un code
//    d'erreur négatif et peut alors recharger le modèle en mode CPU pur.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "OpenCompanionNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct ModelSession {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    // Pointeur détenu par `model` (valide tant que le modèle est chargé) : le patron de
    // formatage du dialogue embarqué dans les métadonnées du GGUF, ou nullptr si absent
    // (llama_chat_apply_template retombe alors sur un format par défaut raisonnable).
    const char* chat_template = nullptr;
    std::atomic<bool> stop_requested{false};
};

bool g_backend_initialized = false;

// Copie un jbyteArray Kotlin en std::string (les octets sont déjà de l'UTF-8
// valide, produits côté Kotlin par String.toByteArray(Charsets.UTF_8)).
std::string byte_array_to_string(JNIEnv* env, jbyteArray array) {
    if (array == nullptr) return {};
    jsize len = env->GetArrayLength(array);
    std::string result(static_cast<size_t>(len), '\0');
    env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte*>(result.data()));
    return result;
}

jbyteArray bytes_to_java_array(JNIEnv* env, const char* data, int32_t len) {
    jbyteArray array = env->NewByteArray(len);
    env->SetByteArrayRegion(array, 0, len, reinterpret_cast<const jbyte*>(data));
    return array;
}

std::string jstring_to_std_string(JNIEnv* env, jstring jstr) {
    // Utilisé uniquement pour le chemin de fichier du modèle : les chemins de
    // fichiers Android ne contiennent quasiment jamais de caractères en
    // dehors du plan multilingue de base, donc GetStringUTFChars convient ici.
    if (jstr == nullptr) return {};
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_opencompanion_app_engine_LlamaBridge_nativeBackendInit(JNIEnv*, jobject) {
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
        LOGI("Backend llama.cpp initialisé");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_opencompanion_app_engine_LlamaBridge_nativeHasVulkanSupport(JNIEnv*, jobject) {
#ifdef OPENCOMPANION_HAS_VULKAN
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_opencompanion_app_engine_LlamaBridge_nativeLoadModel(
        JNIEnv* env, jobject, jstring jModelPath, jint nCtx, jint nGpuLayers, jint nThreads) {
    const std::string model_path = jstring_to_std_string(env, jModelPath);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;

    llama_model* model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (model == nullptr) {
        LOGE("Échec du chargement du modèle GGUF : %s", model_path.c_str());
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(nCtx > 0 ? nCtx : 4096);
    // n_batch/n_ubatch bornent le nombre de tokens qu'un seul appel à llama_decode() peut
    // traiter : un prompt (personnage + historique) qui dépasse cette valeur en une fois faisait
    // planter l'appli (GGML_ASSERT non rattrapable dans llama.cpp), ce qui arrivait dès le 2e ou
    // 3e message d'une conversation avec un personnage un peu détaillé, bien avant d'atteindre la
    // limite réelle du contexte. On aligne désormais n_batch sur n_ctx (borné à 2048 : au-delà,
    // le gain de débit du batching est marginal alors que le pic mémoire grimpe vite sur mobile)
    // — et nativeGenerate() découpe de toute façon tout prompt plus long en plusieurs appels de
    // llama_decode() respectant cette limite, donc cette valeur n'est plus un plafond dur, juste
    // un compromis vitesse/mémoire pour le traitement du prompt.
    ctx_params.n_batch = std::min<uint32_t>(ctx_params.n_ctx, 2048);
    ctx_params.n_ubatch = ctx_params.n_batch;
    const int32_t threads = nThreads > 0 ? nThreads : 4;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Échec de création du contexte llama (mémoire/contexte trop grand pour l'appareil ?)");
        llama_model_free(model);
        return 0;
    }

    auto* session = new ModelSession();
    session->model = model;
    session->ctx = ctx;
    session->vocab = llama_model_get_vocab(model);
    session->chat_template = llama_model_chat_template(model, /* name */ nullptr);

    LOGI("Modèle chargé : n_ctx=%d n_gpu_layers=%d threads=%d patron_dialogue=%s",
         ctx_params.n_ctx, nGpuLayers, threads, session->chat_template != nullptr ? "oui" : "défaut");
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_opencompanion_app_engine_LlamaBridge_nativeApplyChatTemplate(
        JNIEnv* env, jobject, jlong handle, jobjectArray jRoles, jobjectArray jContentsUtf8,
        jboolean addAssistant) {
    if (handle == 0) return nullptr;
    auto* session = reinterpret_cast<ModelSession*>(handle);

    const jsize n_msg = env->GetArrayLength(jRoles);

    // Les std::string doivent rester en vie jusqu'à l'appel à llama_chat_apply_template
    // (llama_chat_message ne fait que pointer vers leurs données).
    std::vector<std::string> roles(n_msg);
    std::vector<std::string> contents(n_msg);
    std::vector<llama_chat_message> chat(n_msg);

    for (jsize i = 0; i < n_msg; i++) {
        auto jRole = reinterpret_cast<jstring>(env->GetObjectArrayElement(jRoles, i));
        roles[i] = jstring_to_std_string(env, jRole);
        env->DeleteLocalRef(jRole);

        auto jContent = reinterpret_cast<jbyteArray>(env->GetObjectArrayElement(jContentsUtf8, i));
        contents[i] = byte_array_to_string(env, jContent);
        env->DeleteLocalRef(jContent);

        chat[i].role = roles[i].c_str();
        chat[i].content = contents[i].c_str();
    }

    std::vector<char> buf(4096);
    int32_t needed = llama_chat_apply_template(
            session->chat_template, chat.data(), static_cast<size_t>(n_msg),
            addAssistant, buf.data(), static_cast<int32_t>(buf.size()));
    if (needed < 0) {
        LOGE("llama_chat_apply_template a échoué (patron de dialogue non reconnu)");
        return nullptr;
    }
    if (needed > static_cast<int32_t>(buf.size())) {
        buf.resize(static_cast<size_t>(needed));
        needed = llama_chat_apply_template(
                session->chat_template, chat.data(), static_cast<size_t>(n_msg),
                addAssistant, buf.data(), static_cast<int32_t>(buf.size()));
    }

    return bytes_to_java_array(env, buf.data(), needed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_opencompanion_app_engine_LlamaBridge_nativeFreeModel(JNIEnv*, jobject, jlong handle) {
    if (handle == 0) return;
    auto* session = reinterpret_cast<ModelSession*>(handle);
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
}

extern "C" JNIEXPORT void JNICALL
Java_com_opencompanion_app_engine_LlamaBridge_nativeAbortGeneration(JNIEnv*, jobject, jlong handle) {
    if (handle == 0) return;
    reinterpret_cast<ModelSession*>(handle)->stop_requested.store(true);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_opencompanion_app_engine_LlamaBridge_nativeTokenCount(
        JNIEnv* env, jobject, jlong handle, jbyteArray jTextUtf8) {
    if (handle == 0) return -1;
    auto* session = reinterpret_cast<ModelSession*>(handle);
    const std::string text = byte_array_to_string(env, jTextUtf8);

    int32_t n = -llama_tokenize(session->vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                 nullptr, 0, true, true);
    return n < 0 ? 0 : n;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_opencompanion_app_engine_LlamaBridge_nativeGenerate(
        JNIEnv* env, jobject, jlong handle, jbyteArray jPromptUtf8, jint nPredict,
        jfloat temperature, jint topK, jfloat topP, jfloat repeatPenalty, jlong seed,
        jobject callback) {
    if (handle == 0) return -1;
    auto* session = reinterpret_cast<ModelSession*>(handle);
    session->stop_requested.store(false);

    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_token_method = env->GetMethodID(callback_class, "onToken", "([B)Z");
    if (on_token_method == nullptr) {
        LOGE("TokenCallback.onToken([B)Z introuvable — API Kotlin/JNI désynchronisée");
        return -2;
    }

    llama_sampler* sampler = nullptr;

    try {
        const std::string prompt = byte_array_to_string(env, jPromptUtf8);

        int32_t n_prompt_max = static_cast<int32_t>(prompt.size()) + 16;
        std::vector<llama_token> prompt_tokens(n_prompt_max);
        int32_t n_prompt_tokens = llama_tokenize(
                session->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                prompt_tokens.data(), n_prompt_max, true, true);
        if (n_prompt_tokens < 0) {
            prompt_tokens.resize(static_cast<size_t>(-n_prompt_tokens));
            n_prompt_tokens = llama_tokenize(
                    session->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                    prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()), true, true);
        }
        if (n_prompt_tokens < 0) {
            LOGE("Échec de tokenisation du prompt");
            return -3;
        }
        prompt_tokens.resize(static_cast<size_t>(n_prompt_tokens));

        // Filet de sécurité : PromptBuilder.kt côté Kotlin est censé garder l'historique dans
        // la fenêtre de contexte, mais un patron de dialogue plus verbeux que prévu (balises,
        // system prompt du modèle, etc.) peut faire déborder le compte réel de quelques tokens.
        // Sans cette troncature, llama_decode() échouerait simplement (contexte plein) plutôt que
        // de planter — mais autant garder la fin du prompt (les échanges les plus récents, donc
        // les plus pertinents) qu'échouer bêtement sur un dépassement de quelques tokens.
        const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(session->ctx));
        if (n_prompt_tokens >= n_ctx) {
            const int32_t keep = std::max(1, n_ctx - 1);
            const int32_t drop = n_prompt_tokens - keep;
            LOGE("Prompt tronqué : %d tokens > n_ctx=%d, on retire les %d plus anciens",
                 n_prompt_tokens, n_ctx, drop);
            prompt_tokens.erase(prompt_tokens.begin(), prompt_tokens.begin() + drop);
            n_prompt_tokens = static_cast<int32_t>(prompt_tokens.size());
        }

        // Contexte réinitialisé à chaque appel : voir note en tête de fichier.
        llama_memory_clear(llama_get_memory(session->ctx), true);

        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        sampler = llama_sampler_chain_init(sparams);
        // Fréquence/présence non nulles (en plus de repeatPenalty) : sans elles, le modèle peut
        // reproduire mot pour mot un tour de phrase déjà utilisé plus tôt dans la même réponse
        // dès que la pénalité de répétition simple ne suffit pas à l'en dissuader — un facteur
        // direct dans les réponses perçues comme figées/répétitives ou qui "reviennent toujours
        // sur la phrase principale". Ces pénalités ne portent que sur les tokens générés pendant
        // cet appel (voir llama_sampler_accept plus bas) : elles ne peuvent rien contre la
        // répétition d'un tour à l'autre, gérée côté prompt par VARIETY_DIRECTIVE (PromptBuilder.kt).
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
                llama_vocab_n_tokens(session->vocab), 64, repeatPenalty, 0.3f, 0.3f));
        if (topK > 0) {
            llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
        }
        if (topP < 1.0f) {
            llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
        }
        if (temperature > 0.0f) {
            llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
            llama_sampler_chain_add(sampler, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
        } else {
            llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
        }

        int32_t n_generated = 0;
        int32_t result_code = 0;
        bool aborted = false;
        char piece_buf[512];

        // Étape 1 : traitement du prompt, découpé en blocs d'au plus n_batch tokens. Un seul
        // appel à llama_decode() avec plus de tokens que n_batch abandonne tout le processus
        // (GGML_ASSERT interne à llama.cpp, pas une exception rattrapable par le try/catch
        // ci-dessus) — un personnage un peu détaillé plus quelques tours d'historique dépassait
        // très vite les 512 tokens qu'on envoyait auparavant en un seul bloc, d'où le crash
        // signalé pour toute conversation un peu suivie. Les positions dans le cache KV sont
        // recalculées automatiquement par llama.cpp à chaque appel (llama_batch_get_one ne fixe
        // pas de position explicite), donc découper ainsi ne change rien au résultat, seulement à
        // la façon dont il est calculé.
        const int32_t n_batch = std::max<int32_t>(1, llama_n_batch(session->ctx));
        int32_t n_fed = 0;
        while (n_fed < n_prompt_tokens) {
            if (session->stop_requested.load()) { result_code = 1; aborted = true; break; }
            const int32_t chunk = std::min(n_batch, n_prompt_tokens - n_fed);
            llama_batch prompt_batch = llama_batch_get_one(prompt_tokens.data() + n_fed, chunk);
            if (llama_decode(session->ctx, prompt_batch) != 0) {
                LOGE("llama_decode a échoué pendant le traitement du prompt (contexte plein ou erreur backend)");
                result_code = -4;
                aborted = true;
                break;
            }
            n_fed += chunk;
        }

        // Étape 2 : génération token par token. Le tout premier tour saute son propre
        // llama_decode() (le prompt vient d'être traité ci-dessus, par blocs) et échantillonne
        // directement depuis les logits laissés par le dernier bloc ; chaque tour suivant décode
        // d'abord le token précédemment généré (un seul token : jamais besoin de découpage ici)
        // avant d'échantillonner le suivant — comportement autoregressif inchangé par ailleurs.
        llama_token new_token = 0;
        bool first_iteration = true;

        while (!aborted) {
            if (session->stop_requested.load()) { result_code = 1; break; }

            if (!first_iteration) {
                llama_batch batch = llama_batch_get_one(&new_token, 1);
                if (llama_decode(session->ctx, batch) != 0) {
                    LOGE("llama_decode a échoué (contexte plein ou erreur backend)");
                    result_code = -4;
                    break;
                }
            }
            first_iteration = false;

            new_token = llama_sampler_sample(sampler, session->ctx, -1);
            llama_sampler_accept(sampler, new_token);

            if (llama_vocab_is_eog(session->vocab, new_token)) {
                break;
            }

            int32_t piece_len = llama_token_to_piece(
                    session->vocab, new_token, piece_buf, sizeof(piece_buf), 0, true);
            if (piece_len > 0) {
                jbyteArray jPiece = bytes_to_java_array(env, piece_buf, piece_len);
                jboolean keep_going = env->CallBooleanMethod(callback, on_token_method, jPiece);
                env->DeleteLocalRef(jPiece);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    result_code = -5;
                    break;
                }
                if (!keep_going) { result_code = 1; break; }
            }

            n_generated++;
            if (nPredict > 0 && n_generated >= nPredict) break;
            if (session->stop_requested.load()) { result_code = 1; break; }
        }

        llama_sampler_free(sampler);
        return result_code;
    } catch (const std::exception& e) {
        LOGE("Exception pendant la génération (pilote GPU/Vulkan instable ?) : %s", e.what());
        if (sampler != nullptr) llama_sampler_free(sampler);
        return -6;
    } catch (...) {
        LOGE("Exception inconnue pendant la génération");
        if (sampler != nullptr) llama_sampler_free(sampler);
        return -7;
    }
}
