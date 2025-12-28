# 🍜 Naruto AI Chat

AI-powered chat application featuring Naruto characters and celebrities with realistic personalities. Choose between SFW and NSFW modes for different conversation styles.

**Powered by Groq Multi-Keys** - Fast, unlimited, uncensored AI with automatic rotation!

---

## ✨ Features

### 💬 Chat Features
- **13 Characters**: 6 Naruto characters + 7 celebrities
- **Dual Modes**: SFW (appropriate) and NSFW (uncensored) conversations
- **Realistic Personalities**: Each character has unique traits and speaking styles
- **Modern UI**: Material Design 3 with smooth animations
- **Context-Aware**: AI remembers your conversation history

### 🔑 Multi-Key System (NEW!)
- **Automatic Rotation**: Multiple Groq API keys rotate automatically
- **No Rate Limits**: Add 3-5 keys for virtually unlimited usage
- **Smart Management**: Automatic error detection and key switching
- **Real-time Stats**: Monitor usage for each key

### 🎨 Media Generation (NEW!)
- **📸 Image Generation**: Create images with Freebox Stable Diffusion (local & unlimited!)
- **🎬 Video Generation**: Generate short animated videos locally
- **Powered by Freebox**: 100% gratuit, local, illimité
- **Pollination AI**: Vignettes et galeries hyper-réalistes

### 👤 Character Details (NEW!)
- **Page de Présentation**: Description physique complète, scénario, tempérament
- **Galerie Photos**: 6 images hyper-réalistes par personnage (Pollination AI)
- **Vignettes Générées**: Images automatiques pour chaque personnage
- **Profil Détaillé**: Âge, taille, traits distinctifs, compétences

---

## 🎭 Characters

### Naruto Characters (6)
- 🍜 **Naruto Uzumaki** - Hyperactive, determined, never gives up
- ⚡ **Sasuke Uchiha** - Serious, calculated, complex past
- 🌸 **Sakura Haruno** - Strong, caring, medical expert
- 📖 **Kakashi Hatake** - Calm, wise, protective
- 💜 **Hinata Hyuga** - Shy, gentle, determined
- 🌙 **Itachi Uchiha** - Mysterious, sacrificing, complex

### Celebrities (7)
- 🎬 **Brad Pitt** - Charismatic, philosophical, legendary actor
- 🌊 **Leonardo DiCaprio** - Environmental, adventurous, intense
- 💪 **Dwayne Johnson** - Motivational, positive, "The Rock"
- 🕷️ **Scarlett Johansson** - Intelligent, direct, sophisticated
- 💎 **Margot Robbie** - Australian, fun, natural
- 📚 **Emma Watson** - Feminist, eloquent, activist
- ✨ **Zendaya** - Elegant, authentic, inspiring

---

## 🚀 Quick Setup (5 minutes!)

### Step 1: Get Groq API Keys (3-5 recommended)

1. Go to: **https://console.groq.com**
2. Sign up with 3-5 different emails:
   - Use Gmail+ trick: your-email+1@gmail.com, your-email+2@gmail.com
   - Or ask friends to create accounts
3. For each account:
   - Go to: **https://console.groq.com/keys**
   - Click "Create API Key"
   - Copy the key (starts with `gsk_`)

### Step 2: Install App

1. Download APK from [Releases](https://github.com/mel805/naruto-ai-chat/releases)
2. Install on Android device (enable "Unknown sources" if needed)

### Step 3: Configure Multi-Keys

1. Open "Naruto AI Chat"
2. Click **⚙️ Settings** (top right)
3. Section "Groq API Keys"
4. Click **"Add Groq Key"** for each key
5. Paste each key (starts with `gsk_`)
6. Click **"Test Connection"** → ✅ Connected
7. **Done!** Enjoy unlimited chat!

### Step 4: (Optional) Enable Images/Videos

1. Create free account on **https://replicate.com**
2. Get API token from **Account → API Tokens**
3. In app Settings, paste Replicate key (starts with `r8_`)
4. Click **"Save"**
5. Now you can generate images and videos!

**📄 Detailed guides:** 
- [Multi-Key Setup](GROQ_MULTIKEY_SETUP.md) ← **NEW!**
- [Old Single-Key Guide](GROQ_API_SETUP.md)

---

## 🎯 Why Groq Multi-Keys?

| Feature | Groq Multi-Keys | Freebox (Old) | Oracle Cloud |
|---------|-----------------|---------------|--------------|
| Setup Time | **5 min** | 30-60 min | 30-60 min |
| Server Required | ❌ **No** | ✅ Yes | ✅ Yes |
| Speed | ⚡ **~200 tok/s** | 🐢 5-10 tok/s | 🐢 Medium |
| Free Capacity | **43K-72K/day** | ∞ Unlimited | Limited |
| Maintenance | ❌ **None** | ✅ Yes | ✅ Yes |
| Reliability | ✅ **99.9%** | Depends | 95% |
| Model Quality | **Llama 3.3 70B** | TinyLlama 1B | Llama 3.2 3B |
| Images/Videos | ✅ **Yes** | ❌ No | ❌ No |
| Auto-Rotation | ✅ **Yes** | N/A | N/A |

---

## 🎮 How to Use

### Basic Chat

1. **Select a character** from the main screen
2. **Choose your mode:**
   - 🔒 **SFW Mode**: Appropriate, respectful conversations
   - 🔓 **NSFW Mode**: Uncensored, adult conversations
3. **Type your message** and send
4. **AI responds** in the character's unique personality

### Generate Images/Videos (NEW!)

1. **During conversation**, click **📸** icon (top right)
2. Choose:
   - **📸 Generate Image**: Creates an image from conversation (~30-60s)
   - **🎬 Generate Video**: Creates animated short video (~2-4min)
3. **Image/Video appears** in the chat
4. **Continue chatting** with visual context!

### Manage API Keys

1. Click **⚙️ Settings** (top right on character selection)
2. **Add/Remove Groq keys** for chat
3. **Add Replicate key** for images/videos
4. **View statistics** for each key
5. **Test connection** anytime

---

## 🛠️ Technical Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material Design 3)
- **Architecture**: MVVM (AndroidViewModel)
- **HTTP**: OkHttp 4.12
- **Storage**: DataStore Preferences
- **Image Loading**: Coil

**APIs:**
- **Chat**: Groq API (Llama 3.3 70B)
- **Images**: Replicate (Stable Diffusion XL)
- **Videos**: Replicate (Stable Video Diffusion)

**Features:**
- **Multi-Key Management**: Custom ApiKeyManager
- **Auto-Rotation**: Intelligent key switching
- **Error Recovery**: Automatic failover
- **Statistics**: Real-time usage tracking

**Requirements:**
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35

---

## 📊 Free Limits (Massive with Multi-Keys!)

**Groq Free Tier (per key):**
- ✅ **14,400 requests per day**
- ✅ **Llama 3.3 70B** model (very intelligent)
- ✅ **No credit card** required
- ✅ **Uncensored** responses
- ✅ **~200 tokens/second** (ultra fast!)

**With 3 Keys:**
- 🚀 **43,200 requests/day** (3x capacity!)
- 🚀 **~4,320 conversations/day**
- 🚀 **Perfect for 150-300 active users**

**With 5 Keys:**
- 🚀 **72,000 requests/day** (5x capacity!)
- 🚀 **~7,200 conversations/day**
- 🚀 **Perfect for 250-500 active users**

**Replicate Free Tier:**
- ✅ **$5 free credit** (no card required)
- ✅ **~2,500 free images** ($0.002 each)
- ✅ **~250 free videos** ($0.02 each)

**More than enough for extensive personal use!**

---

## 🔐 Privacy & Security

- ✅ Conversations sent to Groq via **HTTPS** (encrypted)
- ✅ No data stored on Groq servers after response
- ✅ **You control** your API key
- ✅ **Open source** - audit the code yourself
- ✅ No tracking or analytics

---

## 🐛 Troubleshooting

### "No API key configured"
**Solution:**
1. Go to Settings (⚙️)
2. Add at least 1 Groq API key
3. Test connection

### "Rate limit exceeded" (Auto-Fixed!)
**This is normal!** The app automatically:
1. Detects rate limit
2. Rotates to next key
3. Continues without interruption

**If happens frequently:**
- Add more keys (3-5 recommended)
- Check key statistics in Settings

### All Keys Show Errors
**Causes:**
1. All keys reached daily limit → Wait 24h
2. Invalid keys → Check on console.groq.com
3. Network issue → Check internet

**Solution:**
- Add fresh keys
- Wait for daily reset (midnight UTC)

### Image/Video Generation Fails
**Check:**
1. ✅ Replicate key configured in Settings
2. ✅ Key starts with `r8_`
3. ✅ Free credit available on replicate.com
4. ✅ Good internet connection

### App Crashes
- Ensure Android 8.0+ (API 26+)
- Check logs: `adb logcat | grep Naruto`
- [Report issue](https://github.com/mel805/naruto-ai-chat/issues)

---

## 📱 Requirements

- **Android**: 8.0+ (API 26)
- **Internet**: Required
- **Storage**: ~50-70 MB
- **Groq API Key**: Free from https://console.groq.com

---

## 📄 License

This project is for **educational purposes**.

Naruto characters © Masashi Kishimoto  
AI powered by Meta's Llama 3.3 via Groq

---

## 🤝 Contributing

Contributions are welcome!

- 🐛 [Report bugs](https://github.com/mel805/naruto-ai-chat/issues)
- 💡 [Request features](https://github.com/mel805/naruto-ai-chat/issues)
- 🔧 [Submit pull requests](https://github.com/mel805/naruto-ai-chat/pulls)

---

## 🙏 Credits

- **Naruto** characters © Masashi Kishimoto
- **AI** powered by Meta's Llama 3.3 via [Groq](https://groq.com)
- **Icons** by Google Material Design

---

## 📞 Support

- 📖 [Multi-Key Setup Guide](GROQ_MULTIKEY_SETUP.md) ← **Start Here!**
- 📖 [Old Single-Key Guide](GROQ_API_SETUP.md)
- 📖 [Freebox Setup (Deprecated)](FREEBOX_SETUP.md)
- 🐛 [Report Issues](https://github.com/mel805/naruto-ai-chat/issues)
- 💬 [Discussions](https://github.com/mel805/naruto-ai-chat/discussions)

---

## 🎊 Ready to Chat?

### Quick Start (5 minutes):

1. **Get 3-5 Groq keys**: https://console.groq.com
2. **Download APK**: [Releases](https://github.com/mel805/naruto-ai-chat/releases)
3. **Add keys in Settings**: Click ⚙️ → Add each key
4. **(Optional) Add Replicate key**: For images/videos
5. **Start chatting!** 🎉

### What You Get:

- ✅ **43K+ messages/day** (with 3 keys)
- ✅ **Automatic rotation** (no interruptions)
- ✅ **Image generation** (with Replicate)
- ✅ **Video generation** (with Replicate)
- ✅ **Real-time stats** (monitor usage)
- ✅ **Best AI quality** (Llama 70B)
- ✅ **Ultra-fast** (~200 tok/s)

**Dattebayo!** 🍜

---

## 🔄 Migration from Old System

### Freebox Users

**Old System (Deprecated):**
- ❌ Freebox TinyLlama 1B
- ❌ Requires server maintenance
- ❌ Slow (5-10 tok/s)
- ❌ No images/videos

**New System:**
- ✅ Groq Llama 70B (much better!)
- ✅ No maintenance needed
- ✅ Super fast (200 tok/s)
- ✅ Images & videos support

**To migrate:**
1. Update to latest APK
2. Add Groq keys in Settings
3. Done! Old system automatically replaced

### Single-Key Groq Users

**Upgrade to Multi-Key:**
1. Create 2-4 more accounts
2. Add all keys in Settings
3. Enjoy 3-5x more capacity!

---

## 📈 Version History

**v2.0.0 (Current)** - Dec 26, 2025
- ✨ Multi-key system with auto-rotation
- ✨ Image generation (Stable Diffusion)
- ✨ Video generation (Stable Video Diffusion)
- ✨ Settings screen for key management
- ✨ Real-time statistics
- ✨ Removed Freebox dependency
- 🔥 Replaced LlamaClient → GroqClient
- 🎨 Enhanced UI with media controls

**v1.x** - Previous
- Basic Groq single-key
- Freebox TinyLlama support
- Chat-only functionality
