# RuStore Updater

Android-приложение для скачивания и обновления приложений из каталога [RuStore](https://www.rustore.ru) **без установленного приложения RuStore** и без Obtainium.

Использует тот же публичный API, что и сам RuStore (`backapi.rustore.ru`):
- поиск по каталогу,
- получение информации о приложении (версия, описание, changelog),
- прямая ссылка на APK,
- фоновая проверка обновлений через WorkManager,
- установка APK через системный диалог Package Installer.

## Возможности

- **Поиск приложений** по каталогу RuStore прямо в приложении.
- **Отслеживание обновлений**: добавьте приложение в список — и оно будет проверяться в фоне.
- **Фоновая проверка** обновлений (WorkManager, периодически — настраивается: 1ч / 3ч / 6ч / 12ч / 24ч).
- **Уведомления** о доступных обновлениях.
- **Скачивание APK** через системный DownloadManager (с прогрессом в шторке).
- **Установка** через системный диалог (требуется разрешение «установка из неизвестных источников» — приложение само направит в нужные настройки при первой установке).
- **Автоскачивание** (опционально) — APK скачивается сразу при обнаружении обновления.
- Интерфейс на русском языке.

## Ограничения

Android не позволяет обычным (не системным) приложениям устанавливать APK **молча**. Поэтому:
- обновления всегда проходят через системный диалог «Установить?»;
- фоновая проверка лишь **обнаруживает** обновление и уведомляет пользователя;
- чтобы новая версия установилась, нужно подтвердить системный диалог (или нажать на уведомление «Готово к установке»).

Это не баг, а системное ограничение Android — то же самое делает Obtainium.

## Требования для сборки

- JDK 17+ (проверено на 21) с утилитой `jlink` (полный JDK, не только JRE);
- Android SDK с платформой **android-35** и build-tools **35.0.0**;
- Android Studio или установленные командные утилиты SDK (`sdkmanager`, `adb`).

> Совет: для сборки из командной строки используйте JDK из Android Studio
> (`<android-studio>/jbr`) — в нём есть `jlink`:
> ```
> export JAVA_HOME="/snap/android-studio/current/jbr"
> export ANDROID_HOME="$HOME/Android/Sdk"
> ```

## Сборка

```bash
cd rustore-updater

# Указать JDK с jlink и Android SDK
export JAVA_HOME="/snap/android-studio/current/jbr"
export ANDROID_HOME="$HOME/Android/Sdk"

# Сборка debug-APK
./gradlew :app:assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

### Установка на подключённое устройство

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ru.app.rustoreupdater/.ui.MainActivity
```

При первом нажатии «Установить» Android попросит выдать разрешение на установку
из неизвестных источников для этого приложения — приложение само откроет нужный
экран настроек.

## Подпись релизных сборок (для самообновления)

Android не обновит приложение, если новая версия подписана другим ключом, чем
установленная. Поэтому релизные APK, публикуемые в GitHub Releases (а значит —
используемые механизмом самообновления приложения), должны подписываться **одним
и тем же ключом** при каждой сборке в CI.

CI настроен так: если в репозитории заданы секреты подписи — собирается
нормально подписанный release-APK; если нет — используется debug-подпись (только
для отладочных/PR-сборок). Опубликованный `latest`-релиз **обязательно** должен
собираться с ключом.

### Один раз: создание ключа

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias rustore \
  -keyalg RSA -keysize 2048 -validity 10000
```

Запомните пароли кейстора и ключа. Сконвертируйте кейстор в base64 одной строкой:

```bash
base64 -w0 release.keystore
```

### Добавьте 4 секрета в GitHub

Settings → Secrets and variables → Actions → New repository secret:

| Имя секрета       | Значение                                      |
|-------------------|-----------------------------------------------|
| `KEYSTORE_BASE64` | вывод `base64 -w0 release.keystore`           |
| `KEYSTORE_PASSWORD` | пароль кейстора                             |
| `KEY_ALIAS`       | `rustore` (или ваш алиас)                     |
| `KEY_PASSWORD`    | пароль ключа                                  |

После этого все пушы в `main` и теги `v*` будут собирать подписанные release-APK.

> ⚠️ Один раз после перехода на release-ключ удалите старую debug-версию с
> телефона и поставьте новый release-APK вручную. После этого все будущие
> обновления будут вставать автоматически. **Не теряйте `release.keystore`** —
> без него вы не сможете выпускать обновления (придётся снова переустанавливать
> приложение вручную со сменой ключа).

### Локальная сборка release-APK

Если кейстор есть локально, можно собрать подписанный APK без CI:

```bash
export KEYSTORE_PATH="$PWD/release.keystore"
export KEYSTORE_PASSWORD="..."
export KEY_ALIAS="rustore"
export KEY_PASSWORD="..."
./gradlew :app:assembleRelease
```

APK появится в `app/build/outputs/apk/release/app-release.apk`.

## Структура проекта

```
app/src/main/java/ru/app/rustoreupdater/
├── App.kt                      # Application: инициализация DI, каналов уведомлений, WorkManager
├── Notifier.kt                 # Уведомления об обновлениях / завершении загрузки
├── NotifierActionReceiver.kt   # Обработка кнопки «Установить» в уведомлении
├── di/ServiceLocator.kt        # Ручной DI-контейнер (Retrofit, Room, репозиторий)
├── data/
│   ├── db/                     # Room: TrackedAppEntity, AppDao, AppDatabase
│   ├── network/                # Retrofit: RuStoreApi + DTO под ответ RuStore
│   └── repo/                   # AppRepository, SettingsStore (DataStore)
├── download/                   # ApkDownloader (DownloadManager), ApkDownloadReceiver
├── install/ApkInstaller.kt     # Установка APK через FileProvider + системный диалог
├── work/                       # UpdateCheckWorker + UpdateScheduler (WorkManager)
└── ui/
    ├── MainActivity.kt
    ├── theme/Theme.kt          # Material3 тема, синий брендовый цвет RuStore
    ├── nav/Routes.kt, NavGraph.kt
    └── screens/                # Tracked / Search / Detail / Settings + ViewModel'и
```

## Используемые API RuStore

| Действие | Метод и эндпоинт |
|---|---|
| Поиск | `GET /applicationData/apps?query=&pageNumber=&pageSize=` |
| Инфо о приложении | `GET /applicationData/overallInfo/{appId}` |
| Прямая ссылка на APK | `POST /applicationData/v2/download-link` (тело `{appId, firstInstall:true, ...}`) |

## Технологии

- Kotlin 2.0 + Jetpack Compose (Material 3)
- Navigation-Compose
- Room (локальное хранилище)
- Retrofit + OkHttp + kotlinx.serialization
- Coil (загрузка иконок)
- WorkManager (фоновые проверки)
- DataStore (настройки)
- Android system DownloadManager + FileProvider + PackageInstaller

## Лицензия

Проект создан для личного использования. Использует публичный API RuStore; не
аффилирован с компанией VK / RuStore.
