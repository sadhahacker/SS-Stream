# 🎬 SS-Stream: Universal Cloudstream Multi-Server Plugin

<p align="center">
  <img src="https://raw.githubusercontent.com/recloudstream/cloudstream/master/app/src/main/res/drawable/ic_launcher.png" width="90" alt="Cloudstream Logo" />
  <br>
  <b>A high-performance, modular Cloudstream extension combining multiple streaming backends with real-time in-app priority switching and automatic failovers.</b>
</p>

<p align="center">
  <a href="https://github.com/sadhahacker/SS-Stream/actions"><img src="https://img.shields.io/github/actions/workflow/status/sadhahacker/SS-Stream/build.yml?branch=main&label=Build&style=flat-square" alt="Build Status" /></a>
  <img src="https://img.shields.io/badge/Platform-CloudStream_3-blue?style=flat-square" alt="CloudStream 3" />
  <img src="https://img.shields.io/badge/Language-Kotlin-orange?style=flat-square" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Architecture-Concurrent_Coroutines-success?style=flat-square" alt="Coroutines" />
</p>

---

## 📲 Quick Install in Cloudstream

### ⚡ Option 1: One-Click Install (Android / TV)
If you are viewing this on an Android device with Cloudstream installed, click the badge below to automatically install the repository:

[![Add to Cloudstream](https://img.shields.io/badge/Cloudstream-Add_Repository-00C853?style=for-the-badge&logo=android&logoColor=white)](cloudstreamrepo://raw.githubusercontent.com/sadhahacker/SS-Stream/main/repo.json)

---

### 📋 Option 2: Manual Installation

1. Open **Cloudstream** on your phone or Android TV.
2. Go to **Settings (⚙️)** ➔ **Extensions**.
3. Tap **Add Repository**.
4. Enter the following repository details:

| Setting | Value |
| :--- | :--- |
| **Repository Name** | `SS-Stream` |
| **Repository URL** | `https://raw.githubusercontent.com/sadhahacker/SS-Stream/main/repo.json` |

5. Tap **Download**.
6. Scroll to **StreamCore** in the plugin list and tap **Install**.

---

## ✨ Features

- **🚀 4-in-1 Parallel Multi-Server Engine**: Queries multiple top-tier streaming CDNs simultaneously. Links appear instantly as each server resolves.
- **⚙️ In-App Priority & Server Manager**: Change your primary stream server or toggle servers ON/OFF directly inside Cloudstream without rebuilding the app.
- **🛡️ Isolated Failover Protection**: If one server is slow, rate-limited, or down, the other providers seamlessly step in.
- **🏷️ Clear Stream Labeling**: All video links in the player are prefixed with their originating provider (`[VidCore] 1080p`, `[VidLink] Auto`, `[Videasy] 1080p`).
- **🍿 Rich TMDB Catalog**: Home screen discovery for Trending Movies, Trending TV, and Popular categories with trailers, ratings, and plot summaries.

---

## 🌐 Included Streaming Backends

| Server | Base URL | Quality | Default Priority |
| :--- | :--- | :--- | :--- |
| **VidCore** | `vidcore.org` | 1080p / 720p / HLS | 🥇 Primary (100) |
| **VidLink** | `vidlink.pro` | 1080p / Multi-source | 🥈 Secondary (90) |
| **Videasy** | `player.videasy.to` | 1080p / HLS | 🥉 Tertiary (80) |
| **EmbedMaster** | `embedmaster.link` | 1080p / 720p | Priority 70 |
| **AutoEmbed** | `autoembed.co` | Multi-quality | Backup (60) |
| **2Embed** | `2embed.cc` | Multi-quality | Backup (50) |
| **LordFlix** | `lordflix.to` | Multi-quality | Backup (40) |
| **VidLove** | `player.vidlove.cc` | 720p / 1080p | Backup (30) |

---

## ⚙️ How to Change Primary Server In-App

You can change which server takes #1 priority directly inside Cloudstream:

1. In Cloudstream, go to **Settings ➔ Extensions ➔ StreamCore**.
2. Tap the **Settings (⚙️ Gear Icon)** next to StreamCore.
3. Select your preferred **Primary Source** (e.g. *VidCore*, *VidLink*, *Videasy*).
4. Tap **Set as Top Priority**.
5. The chosen server is immediately boosted to the top of your playback list!

---

## 🧩 How to Add New Providers in the Future

The codebase is built on a zero-hardcoding modular architecture. To add any new provider, simply edit **`StreamSources.kt`**:

```kotlin
// StreamCoreProvider/src/main/kotlin/com/streamcore/StreamSources.kt

val REGISTERED_SOURCES: List<StreamingSource> = listOf(
    // Add your new provider in just 1 line:
    PatternSource(
        name = "NewProvider",
        moviePattern = "https://newprovider.com/embed/movie/%d",
        tvPattern = "https://newprovider.com/embed/tv/%d/%d/%d",
        priority = 95,
        enabled = true
    ),
    // ... existing sources
)
```

Commit and push to GitHub — GitHub Actions will automatically recompile your `.cs3` extension and Cloudstream will prompt you to update!

---

## 🛠️ Local Development & Building

```bash
# Clone the repository
git clone https://github.com/sadhahacker/SS-Stream.git
cd SS-Stream

# Compile the plugin (.cs3) locally
./gradlew StreamCoreProvider:make

# Deploy directly to connected Android device via ADB
./gradlew StreamCoreProvider:deployWithAdb
```

The compiled output will be located at:
`StreamCoreProvider/build/StreamCoreProvider.cs3`

---

## ⚖️ License & Disclaimer

This project is created for educational and personal research purposes. This extension does not host, upload, or store any media files; it aggregates public embed metadata. All media is provided by unaffiliated third-party services.
