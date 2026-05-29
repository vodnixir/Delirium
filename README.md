<div align="center">

# 📸 Delirium

**Сделай фото на одном телефоне — увидь его на другом.**
Без открытия приложения: фото прилетает прямо на виджет домашнего экрана.

Лёгкое Android-приложение для близких людей: добавляй друзей по коду,
обменивайтесь моментами, и пусть последнее фото каждого друга живёт
прямо на главном экране.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Glance](https://img.shields.io/badge/Glance%20Widget-1.1.1-3DDC84?logo=android&logoColor=white)](https://developer.android.com/jetpack/androidx/releases/glance)
[![Firebase](https://img.shields.io/badge/Firebase-33.7-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![minSdk](https://img.shields.io/badge/minSdk-29-555)](#)
[![License](https://img.shields.io/badge/license-private-lightgrey)](#)

</div>

---

## ✨ Что умеет

- 🔐 **Анонимный вход** — никаких регистраций, аккаунт создаётся сам.
- 👥 **Друзья по 6-значному коду** — один генерирует код, другой вводит. Сколько угодно связей.
- 📷 **Камера** — снимок с CameraX, сжатие в JPEG перед отправкой.
- 🖼 **Галерея** — отправка готового фото через системный Photo Picker (без лишних разрешений).
- 🎨 **Рисование** — холст на Compose Canvas: палитра, толщина, ластик, undo, можно рисовать поверх фото.
- 🧩 **Виджет на главном экране** — отдельный виджет на каждого друга, показывает его последнее фото.
- ⚡ **Мгновенная доставка** — Cloud Function шлёт push (FCM), телефон скачивает фото и обновляет виджет.
- 🔁 **Fallback на WorkManager** — если push не дошёл, периодический воркер (~30 мин) всё равно подтянет свежее фото.

---

## 🏗 Архитектура

```
┌──────────────────────────────────────────────────────────────────┐
│                        Android-приложение                          │
│                                                                    │
│  UI (Compose)        ViewModel          Data / Repositories        │
│  ─────────────       ──────────         ────────────────────       │
│  Splash              SplashVM           AuthRepository             │
│  Friends ───────────►FriendsVM ────────►ConnectionRepository       │
│  AddFriend           AddFriendVM        PhotoRepository ──┐        │
│  Feed                FeedVM             PhotoCache         │        │
│  Camera / Draw       CameraVM/DrawVM    PreferencesRepo    │        │
│  WidgetConfig        WidgetConfigVM     FcmTokenSyncer     │        │
│                                                            │        │
│  Glance Widget ◄── WidgetUpdater ◄── WidgetPhotoSync ◄─────┤        │
│       ▲                                  ▲                 │        │
│       │                                  │                 │        │
│  WidgetRefreshWorker (WorkManager)   DeliriumMessagingService       │
└───────────────────────────────────────┬──────────────────┬────────┘
                                         │ FCM push          │ upload / read
                                         │                   ▼
┌────────────────────────────────────────┴───────────────────────────┐
│                              Firebase                                │
│  Auth (anonymous) · Firestore · Storage · Cloud Messaging            │
│                                                                      │
│  Cloud Function  onPhotoCreated:                                     │
│    new photo doc → найти получателей в connection → отправить FCM     │
└──────────────────────────────────────────────────────────────────────┘
```

Слои простые и без фреймворков DI: ручной `AppContainer` раздаёт синглтоны
репозиториев, экраны на Compose + `ViewModel`, навигация на Navigation-Compose.

---

## 🗂 Модель данных (Firestore)

| Коллекция | Документ | Содержимое |
|-----------|----------|-----------|
| `users/{uid}` | пользователь | `displayName`, `fcmToken` |
| `connections/{id}` | связь двух людей | `members[]`, `names{uid→имя}`, `createdAt`, `lastPhotoAt`, `lastPhotoUrl` |
| `photos/{id}` | фото | `connectionId`, `senderId`, `storageUrl`, `caption?`, `createdAt` |
| `invites/{code}` | код-приглашение | `connectionId`, `createdBy`, `createdAt`, `expiresAt`, `used` |

Свои связи приложение слушает в реальном времени:
`connections.whereArrayContains("members", uid)`.
Файлы фото лежат в Storage по пути `photos/{connectionId}/...`, виджет кэширует
последнее фото каждой связи в `filesDir/widget/{connectionId}.jpg`.

---

## 🔄 Путь фотографии

```
[Телефон A: камера / галерея / рисунок]
        │  сжатие в JPEG
        ▼
  PhotoRepository.upload()
   ├─ Storage:   photos/{connectionId}/{photoId}.jpg
   └─ Firestore: photos/{photoId}
        │
        ▼
[Cloud Function onPhotoCreated]
   ├─ читает members связи
   ├─ выбирает получателей (все, кроме отправителя)
   └─ шлёт high-priority FCM data-message {photoId, photoUrl, connectionId, senderId}
        │
        ▼
[Телефон B: DeliriumMessagingService]
   ├─ скачивает photoUrl
   ├─ PhotoCache → widget/{connectionId}.jpg
   └─ WidgetUpdater.refreshConnection()
        │
        ▼
[Телефон B: Glance-виджет перерисовывается]
        ▲
        └─ если push не пришёл → WidgetRefreshWorker подтянет позже
```

---

## 🧰 Технологии

| Слой | Чем |
|------|-----|
| Язык | Kotlin 2.1, корутины |
| UI | Jetpack Compose (BOM 2024.12), Material 3, Navigation-Compose |
| Виджет | Jetpack Glance 1.1 |
| Камера | CameraX 1.4 |
| Изображения | Coil, androidx.exifinterface |
| Хранение настроек | DataStore Preferences |
| Фоновые задачи | WorkManager |
| Бэкенд | Firebase Auth · Firestore · Storage · Cloud Messaging |
| Functions | TypeScript, Cloud Functions v2 (Node 22), регион `europe-west3` |
| Сборка | Gradle 8.7 (AGP 8.7.3), version catalog |

---

## 📁 Структура репозитория

```
app/                                  Android-модуль
  src/main/kotlin/dev/vodnixir/delirium/
    data/
      auth/            AuthRepository          анонимный вход
      connection/      ConnectionRepository    друзья, коды-приглашения
      photo/           PhotoRepository          лента, загрузка фото
                       PhotoCache               кэш фото для виджета
      local/           PreferencesRepository    DataStore (имя, кэш per-connection)
      messaging/       FcmTokenSyncer           синхронизация FCM-токена
      AppContainer     ручной DI (синглтоны репозиториев)
    domain/model/      Models.kt                Connection, Photo, Invite, ...
    ui/
      splash/  friends/  feed/  camera/  draw/  widget/  nav/  theme/  util/
    widget/
      DeliriumGlanceWidget    Glance-виджет (рисует фото по connectionId)
      DeliriumWidgetReceiver  AppWidget receiver
      WidgetConfig*           привязка виджета к конкретному другу
      WidgetUpdater / WidgetPhotoSync / WidgetRefreshWorker / WidgetWorkScheduler
    messaging/
      DeliriumMessagingService  приём FCM → скачать → кэш → обновить виджет
  src/main/res/        иконки, темы, метаданные виджета
functions/             Cloud Function (TypeScript): onPhotoCreated → FCM
firestore/             security rules + indexes + заметки по деплою
firebase.json          конфиг Firebase CLI
gradle/libs.versions.toml   version catalog
```

---

## 🚀 Запуск

> Сборка делается в Android Studio: нужен Android SDK (API 35) и
> `google-services.json` для Firebase-проекта.

**Приложение**
```sh
# открыть папку проекта в Android Studio → дать Gradle синхронизироваться
# (предложит установить Android API 35 — согласиться) → Run
```

**Бэкенд (Firebase)**
```sh
npm install -g firebase-tools
firebase login

cd functions && npm install && cd ..

# деплой правил, индексов, storage и функции
firebase deploy

# минимум, чтобы убрать PERMISSION_DENIED:
firebase deploy --only firestore:rules,storage
```

Проект Firebase указан в `.firebaserc`. Регион Cloud Function —
`europe-west3` (поменять в `functions/src/index.ts`, если Firestore в другом).

**Сквозной тест на двух телефонах**
1. Телефон A: ввести имя → «Создать код» → скопировать.
2. Телефон B: ввести имя → «Ввести код друга» → вставить → Join.
3. Телефон A: долгий тап по экрану → Виджеты → перетащить виджет Delirium → выбрать друга.
4. Телефон B: сделать фото.
5. Телефон A: виджет обновится за секунды; в ленте обоих появится фото.

> 💡 Если виджет перестаёт обновляться через время — на телефоне
> Настройки → Приложения → Delirium → Батарея → «Без ограничений»
> (на Xiaomi/Huawei/Samsung ещё включить «Автозапуск»). Классическая
> проблема Doze / OEM-киллеров фоновых процессов.

---

## 📌 Статус

Клиент и бэкенд реализованы полностью (модель `connections` вместо старой
`couple`, виджеты per-friend, галерея, рисование, FCM + WorkManager). Осталось:
финальная сборка/прогон UI в Android Studio и деплой Firebase. Подробности и
чек-лист — в [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md).
