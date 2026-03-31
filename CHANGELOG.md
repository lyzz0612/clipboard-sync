# Changelog

本文件由 GitHub Actions 在发布 Android 版本标签时自动生成和更新。
## v1.0.2 - 2026-03-31

### Documentation

- docs(changelog): update for v1.0.1

- docs: update README with demo credentials for example site

- docs: update README to remove demo video and add splash image

- Removed the demo video from the README to streamline content.
- Added a splash image link for a visual representation of the project.


### Features

- feat: enhance Android build workflow with build mode selection

- Added `build_mode` input to the GitHub Actions workflow for manual triggers, allowing users to choose between `release` and `debug` builds.
- Updated APK output naming and paths based on the selected build mode.
- Enhanced README documentation to clarify the new build mode options and their effects on APK generation.
- Integrated file logging capabilities in debug mode for improved troubleshooting.

- feat: migrate from KV to D1 database for clipboard synchronization

- Updated the application to use D1 database instead of KV for storing user clips and settings.
- Modified deployment workflows to include D1 migrations and adjusted environment variable requirements.
- Enhanced local development documentation to reflect changes in database setup and usage.
- Refactored code to accommodate new database interactions, including user authentication and clip management.
- Updated README files to clarify the new D1 storage implementation and its implications for users.

- feat: enhance clipboard synchronization and update documentation

- Added a demo video to the README for better user guidance.
- Refactored ClipboardAccessibilityService to improve synchronization handling with a new atomic boolean for IME sync state.
- Updated ClipboardSyncWorker to utilize batch clip data for clipboard operations, enhancing performance and reliability.
- Removed outdated worker-related images from the documentation.


### Fixes

- fix: update Android build workflow to output APK instead of ZIP

- Changed the artifact naming from ZIP to APK in the build-android workflow.
- Updated the publish-release workflow to reflect the change in artifact type from ZIP to APK.
- Revised README to clarify the new artifact handling in the release process.


### Others

- Change demo image link to clickable format

Updated demo image link in README.md to use a clickable format.


### Refactors

- refactor: rename and update Android build workflow for clarity

- Renamed the workflow from `Release Android` to `Build Android` to better reflect its purpose.
- Updated documentation to clarify the workflow's role in building APKs instead of ZIP files.
- Enhanced README files to provide clearer instructions on the build process and artifact handling.
- Adjusted artifact naming conventions to align with the new workflow structure.


## v1.0.1 - 2026-03-30

### Documentation

- docs(changelog): update for v1.0.0


### Refactors

- refactor: update release workflows and documentation for improved clarity

- Consolidated release processes by introducing the `Publish Release` workflow, which now handles version tagging, changelog generation, and artifact uploads.
- Updated `build-android` and `deploy-worker` workflows to support manual triggers and internal calls from `Publish Release`.
- Removed the outdated `update-changelog` workflow, integrating its functionality into the new release process.
- Enhanced documentation in README files to reflect changes in the release workflow and clarify the roles of each workflow in the build and deployment process.


## v1.0.0 - 2026-03-30

### Chores

- chore(android): 使用 mipmap 多密度启动图标并更新 Manifest

Made-with: Cursor


### Features

- feat(android): 单击列表条目复制该条到剪贴板

Made-with: Cursor

- feat(android): integrate FileLogger for enhanced logging across the application

- Initialized FileLogger in key components including ClipboardSyncApp, MainActivity, and BootReceiver.
- Added detailed logging for ClipboardRepository methods to track API interactions and errors.
- Enhanced ClipboardAccessibilityService and ClipboardSyncWorker with logging for better debugging.
- Updated MainViewModel and SetupViewModel to log user actions and errors during clipboard operations and authentication processes.

- feat(android): implement QR code login functionality and enhance UI

- Added QR code login feature, allowing users to log in via a scanned code generated from the web interface.
- Updated AndroidManifest.xml to include camera permissions and features.
- Introduced new API models for QR login and redeem requests.
- Enhanced SetupScreen to support QR scanning and display relevant UI elements.
- Implemented backend logic for handling QR session creation and redemption.
- Improved user experience with informative prompts and error handling during the login process.

- feat(worker): implement QR modal for app login and enhance UI elements

- Introduced a QR modal for app login, replacing the previous QR panel for improved user experience.
- Enhanced styling and layout of the QR modal, including a backdrop and close button.
- Updated QR code generation logic and ensured proper localization for prompts and error messages.
- Improved accessibility by adding aria-labels and titles to relevant elements.

- feat(android): enhance PrefsManager and MainScreen with permission guide and user feedback

- Updated PrefsManager to manage user visibility of the permission guide and added methods for handling this state.
- Enhanced MainScreen to display an alert dialog for the permission guide, prompting users to enable accessibility services for clipboard synchronization.
- Improved user experience by showing the current username in the app's top bar and providing feedback on sync delays.
- Refactored ClipboardRepository to reset last sync time upon token clearance for better state management.

- feat: enhance build process and documentation for Android release

- Updated the GitHub Actions workflow to support automatic release builds triggered by version tags.
- Added environment variable handling for Android signing configurations in the build.gradle.kts file.
- Introduced a CHANGELOG.md file for tracking changes, generated automatically during the release process.
- Updated README and Android README to reflect new build and release processes, including signing and environment variable requirements.
- Removed outdated deploy documentation and added local development instructions for the Worker.

- feat: add admin module and enhance registration management

- Introduced an independent admin module for user management, allowing listing and bulk deletion of users, as well as configuration of registration permissions.
- Updated API endpoints for admin functionalities, including login, user listing, and settings management.
- Enhanced the registration process with a status check endpoint to indicate whether registration is enabled or disabled.
- Improved documentation to reflect new admin features and updated environment variable requirements for local development.


### Fixes

- fix: workflow branches use master, add cloud-paste trigger

Made-with: Cursor

- fix: gradlew CRLF 换行符导致 Linux CI 构建失败

Made-with: Cursor

- fix: gradlew JVM 参数在 POSIX sh 下被误解析为主类名

Made-with: Cursor

- fix: 替换为完整的 gradle-wrapper.jar（含 GradleWrapperMain）

Made-with: Cursor

- fix: 使用 Gradle 8.11.1 官方 gradlew/gradlew.bat，并为 *.jar 声明 binary

Made-with: Cursor

- fix(android): 补充启动图标、Material 依赖与主题以通过资源链接

Made-with: Cursor

- fix(android): isMiui() 返回类型与语法在 CI Kotlin 下可编译

Made-with: Cursor

- fix(android): 刷新时同步最新一条到剪贴板(主线程)、顶部进度条与 Snackbar 提示

Made-with: Cursor

- fix(android): update FloatingActionButton behavior to adjust opacity based on loading state


### Others

- Initial commit

- first commit from vibre coding

- remove .github from cloud-paste, update wrangler.toml KV placeholder

Made-with: Cursor

- 重新设计网页界面：暖色深色主题、现代布局与多语言支持

- 替换灰白样式为深色毛玻璃设计（暖炭灰/琥珀金配色）

- 引入 Inter 字体、入场动画、浮动光晕效果

- 剪贴条目增加一键复制按钮和相对时间显示

- 根据浏览器语言自动切换中英文界面

Made-with: Cursor

- 更新 README 和文档，添加多语言支持及相关说明

- 将 README 和 Android 文档中的标题翻译为中文
- 在 API 文档中更新术语为中文
- 增加 Worker 文档中的环境变量和构建说明
- 在前端代码中实现语言切换功能，支持中英文界面切换

Made-with: Cursor

- 添加workflow，更新README


### Refactors

- refactor(android): update app name and enhance logging behavior

- Changed the app label in AndroidManifest.xml to reference the string resource for better localization.
- Modified logging behavior in ClipboardRepository to conditionally enable logging based on FileLogger settings.
- Removed unnecessary log statements in BootReceiver and ClipboardAccessibilityService to streamline logging.
- Updated UI components to use the localized app name in various screens, improving consistency across the application.

- refactor(worker): limit the number of clips per user and enhance clip retrieval logic

- Introduced a constant to define the maximum number of clips each user can retain.
- Updated the clip retrieval functions to ensure only the most recent clips are returned, improving performance and user experience.
- Adjusted the logic for setting the last sync time in ClipboardRepository to prevent incorrect watermark advancement.


