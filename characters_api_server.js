const express = require('express');
const cors = require('cors');
const fs = require('fs').promises;
const path = require('path');

const app = express();
const PORT = 33500;
const CHARACTERS_DB = '/home/bagbot/characters_database.json';
const IMAGES_DIR = '/home/bagbot/character_images';

// Middleware
app.use(cors());
app.use(express.json());
app.use('/images', express.static(IMAGES_DIR));

// Logger
app.use((req, res, next) => {
    console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
    next();
});

// ============================================
// ROUTES API
// ============================================

// GET /api/characters - Liste tous les personnages
app.get('/api/characters', async (req, res) => {
    try {
        const data = await fs.readFile(CHARACTERS_DB, 'utf8');
        const db = JSON.parse(data);
        res.json({
            success: true,
            version: db.version,
            lastUpdate: db.lastUpdate,
            count: db.characters.length,
            characters: db.characters
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// GET /api/characters/:id - Détails d'un personnage
app.get('/api/characters/:id', async (req, res) => {
    try {
        const data = await fs.readFile(CHARACTERS_DB, 'utf8');
        const db = JSON.parse(data);
        const character = db.characters.find(c => c.id === req.params.id);
        
        if (!character) {
            return res.status(404).json({
                success: false,
                error: 'Character not found'
            });
        }
        
        res.json({
            success: true,
            character
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// POST /api/characters - Ajouter personnage
app.post('/api/characters', async (req, res) => {
    try {
        const data = await fs.readFile(CHARACTERS_DB, 'utf8');
        const db = JSON.parse(data);
        
        const newCharacter = req.body;
        
        // Vérifier si ID existe
        if (db.characters.find(c => c.id === newCharacter.id)) {
            return res.status(400).json({
                success: false,
                error: 'Character ID already exists'
            });
        }
        
        db.characters.push(newCharacter);
        db.lastUpdate = new Date().toISOString();
        
        await fs.writeFile(CHARACTERS_DB, JSON.stringify(db, null, 2));
        
        res.json({
            success: true,
            character: newCharacter
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// PUT /api/characters/:id - Modifier personnage
app.put('/api/characters/:id', async (req, res) => {
    try {
        const data = await fs.readFile(CHARACTERS_DB, 'utf8');
        const db = JSON.parse(data);
        
        const index = db.characters.findIndex(c => c.id === req.params.id);
        
        if (index === -1) {
            return res.status(404).json({
                success: false,
                error: 'Character not found'
            });
        }
        
        db.characters[index] = { ...db.characters[index], ...req.body };
        db.lastUpdate = new Date().toISOString();
        
        await fs.writeFile(CHARACTERS_DB, JSON.stringify(db, null, 2));
        
        res.json({
            success: true,
            character: db.characters[index]
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// DELETE /api/characters/:id - Supprimer personnage
app.delete('/api/characters/:id', async (req, res) => {
    try {
        const data = await fs.readFile(CHARACTERS_DB, 'utf8');
        const db = JSON.parse(data);
        
        const index = db.characters.findIndex(c => c.id === req.params.id);
        
        if (index === -1) {
            return res.status(404).json({
                success: false,
                error: 'Character not found'
            });
        }
        
        const deleted = db.characters.splice(index, 1)[0];
        db.lastUpdate = new Date().toISOString();
        
        await fs.writeFile(CHARACTERS_DB, JSON.stringify(db, null, 2));
        
        res.json({
            success: true,
            character: deleted
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// GET /api/stats - Statistiques
app.get('/api/stats', async (req, res) => {
    try {
        const data = await fs.readFile(CHARACTERS_DB, 'utf8');
        const db = JSON.parse(data);
        
        const stats = {
            totalCharacters: db.characters.length,
            males: db.characters.filter(c => c.category.includes('male')).length,
            females: db.characters.filter(c => c.category.includes('female')).length,
            withGallery: db.characters.filter(c => c.galleryNSFW && c.galleryNSFW.length > 0).length,
            version: db.version,
            lastUpdate: db.lastUpdate
        };
        
        res.json({
            success: true,
            stats
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Health check
app.get('/health', (req, res) => {
    res.json({
        success: true,
        status: 'online',
        timestamp: new Date().toISOString()
    });
});

// 404
app.use((req, res) => {
    res.status(404).json({
        success: false,
        error: 'Endpoint not found'
    });
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`
╔═══════════════════════════════════════════════╗
║  🎭 Characters API Server                     ║
║  Port: ${PORT}                                   ║
║  Database: ${CHARACTERS_DB}        ║
║  Images: ${IMAGES_DIR}            ║
╚═══════════════════════════════════════════════╝
    `);
});
