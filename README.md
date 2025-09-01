# **名片助理 (Business Card Assistant) \- 系統架構與實施藍圖 (v3.0)**

## **第 1 節：基礎架構與專案結構**

本節旨在為「名片助理」應用程式奠定堅實的架構基礎。此處所做的決策——涉及專案結構、架構模式及核心函式庫的選擇——將直接影響應用程式的可維護性、可擴展性及整體開發效率。

### **1.1 Compose Multiplatform 專案設定**

**目標：** 建立一個標準化、穩固的 Kotlin Multiplatform (KMP) 專案結構，以支援 Android 與 iOS 平台的共享 UI 及業務邏輯。

**實施細節：** 專案將使用官方的 JetBrains KMP 精靈或 IntelliJ IDEA/Android Studio 外掛程式進行初始化，並選用「Compose Multiplatform Application」範本 1。此操作將生成一個包含共享程式碼的

composeApp 模組，以及一個用於 Xcode 專案的 iosApp 模組。

**源集 (Source Set) 結構：** composeApp 模組將依據標準源集進行組織：

* commonMain：包含所有共享的業務邏輯、UI (Composables)、ViewModels、資料模型及倉儲 (Repository) 介面。MVI 架構、Decompose 元件、SQLDelight 結構定義 (schema) 及 expect 宣告都將位於此處 1。
* androidMain：提供針對 Android 平台的 actual 實作，用於處理平台原生 API (例如資料庫驅動程式、圖片選擇器)，並包含託管 Compose UI 的主要 Activity。
* iosMain：提供針對 iOS 平台的 actual 實作，並包含將共享 Compose UI 橋接至 iOS 應用程式的 UIViewController。

**Gradle 配置：** build.gradle.kts 檔案將被配置以引入 Compose Multiplatform、Decompose、SQLDelight、Firebase 及技術棧中指定的其他函式庫依賴 3。

### **1.2 MVI-Decompose 核心架構**

**目標：** 採用 MVI (Model-View-Intent) 模式實現可預測的單向資料流，並結合 Decompose 函式庫進行穩健的導航與生命週期管理。

MVI 模式 5：

* **Model (State)：** 使用單一、不可變的 ViewState 資料類別來表示特定畫面的完整狀態 (例如 CardListState、CardDetailState)。這確保了單一事實來源 (Single Source of Truth) 7。狀態將對所有 UI 的可能性進行建模，包括載入中、成功 (包含資料) 及錯誤狀態 6。
* **View (Composable)：** Composable 函數將設計為無狀態的，它們接收 ViewState 作為參數，並在回應使用者互動 (如按鈕點擊) 時發出 ViewAction。UI 將使用 collectAsState() 來觀察狀態變化 6。
* **Intent (Action/Event)：** 使用者互動將被建模為一個 sealed class 型別的 ViewAction，由 UI 層發送至 ViewModel 進行處理 6。

Decompose 導航與生命週期管理 3：

* **元件化架構：** 每個畫面或邏輯 UI 區塊將由一個 Component 管理 (例如 RootComponent、CardListComponent、CardDetailComponent)。RootComponent 將負責管理主要的導航堆疊 3。
* **導航堆疊：** Decompose 的 ChildStack 將用於管理導航返回堆疊。畫面轉換將透過將 Parcelable 的配置物件推入 (push) 或彈出 (pop) 此堆疊來驅動 3。
* **生命週期管理：** 每個 Component 都會接收一個 ComponentContext，它提供了感知生命週期的協程作用域 (coroutine scopes) 及一個 InstanceKeeper，用於在設定變更 (如螢幕旋轉) 和程序終止後保留 ViewModel 實例。這項功能對於跨平台應用的穩定性至關重要 10，並以平台無關的方式優雅地解決了 ViewModel 的生命週期管理問題。

這種 MVI 與 Decompose 的組合並非偶然，而是一種協同效應的配對。MVI 提供了可預測的狀態管理模式，但其本身並未解決導航或生命週期的挑戰。Decompose 則專門處理這些跨平台的痛點。由 Decompose 提供的 ComponentContext 提供了運行 MVI ViewModel 協程所需的、具備生命週期感知能力的完美作用域，從而防止資源洩漏並確保狀態得以保存。透過將 MVI ViewModel 巢套於 Decompose Component 之內，我們利用 Decompose 的生命週期管理能力來驅動 MVI 狀態機，從而建構出一個比單獨使用 MVI 更具韌性的架構。

### **1.3 依賴管理與 KMP Firebase SDK 選型**

**目標：** 為 KMP 專案選擇一個全面的 Firebase SDK，並建立一套依賴注入策略。

**KMP Firebase SDK：** 官方的 Firebase SDKs 尚未提供完整的 KMP 支援 11。社群已填補了此一缺口，因此必須在現有的函式庫中做出關鍵決策。

* gitliveapp/firebase-kotlin-sdk 13：  
  這是一個全面的、Kotlin-first 的 SDK，其 API 設計與官方 Android SDK 非常相似。它廣泛使用協程 (suspend 函數) 和 Flow，與現代 Kotlin 開發實踐完美契合。其涵蓋了 Authentication、Firestore、Functions 等多項服務，是本專案的有力競爭者。其設定需要在 iOS 端手動連結 Firebase pods 13。
* KMPAuth 16：  
  此函式庫更為專注，主要目標是透過 Firebase 實現身份驗證 (Google、Apple、GitHub)。雖然其在特定領域表現出色，但並未涵蓋 Firestore 或 Functions，這意味著我們仍需尋找其他解決方案或最終還是選擇 gitliveapp。

**決策：** gitliveapp/firebase-kotlin-sdk 是本專案的最佳選擇，因其廣泛涵蓋了所有必需的 Firebase 服務 (Auth、Firestore、Functions)。其 Kotlin-first 的設計理念，結合 suspend 和 Flow 的使用，與我們的 MVI 架構完美對齊。

**依賴注入：** 將採用輕量級的 DI 框架 (如 Koin) 或手動 DI 的方式，為 Decompose 元件提供依賴項 (例如 Repositories、Use Cases)。這將在共享模組中進行配置，而平台特定的依賴項 (如資料庫驅動程式) 將透過 expect/actual 工廠模式提供。

由於缺乏官方支援，依賴像 gitliveapp 這樣的第三方 KMP Firebase SDK 是一項策略上的必要之舉。這也意味著專案的進程將依賴於該第三方函式庫的維護時程與 API 覆蓋範圍。這帶來了一個輕微的專案風險：如果 Firebase 發布新功能，KMP SDK 可能會延遲支援。然而，對於本專案而言，這種權衡 (以開發速度和共享程式碼換取對官方新功能的即時存取) 是可以接受的，因為所需的核心 Firebase 服務都已相當成熟，並在該函式庫中獲得了良好支援 12。

---

## **第 2 節：資料持久化與同步層**

本節將詳細闡述雙模式資料策略，這是區分免費版與付費版應用程式的核心需求。

### **2.1 使用 SQLDelight 的本地優先儲存 (免費版)**

**目標：** 為免費版使用者實作一個高效能、型別安全的本地資料庫。

實施細節 4：

* **Gradle 設定：** 將應用 SQLDelight Gradle 外掛程式，並將驅動程式依賴 (android-driver、native-driver、coroutines-extensions) 添加至對應的源集 18。
* **結構定義：** 將在 commonMain/sqldelight 目錄下建立一個 BusinessCard.sq 檔案。此檔案將包含用於名片資料模型的 CREATE TABLE 語句，以及所有必要的 CRUD (建立、讀取、更新、刪除) 查詢，並附帶標籤 (例如 insertCard:、selectAllCards:) 18。
* **型別安全 API 生成：** Gradle 外掛程式將根據 .sq 檔案自動生成一個 Kotlin 資料庫介面 (AppDatabase) 及型別安全的查詢函數 4。
* **驅動程式實作：** 將使用 expect/actual 模式來提供平台特定的 SqlDriver 實例。
  * androidMain：一個 actual 工廠將提供 AndroidSqliteDriver 4。
  * iosMain：一個 actual 工廠將提供 NativeSqliteDriver 4。
* **倉儲層 (Repository)：** 在 commonMain 中的 CardRepository 將抽象化資料庫操作，使用生成的 SQLDelight 查詢，並將資料以 Kotlin Flow 的形式暴露給 UI 層，以實現響應式更新 18。

### **2.2 使用 Cloud Firestore 的雲端同步儲存 (付費版)**

**目標：** 為付費版使用者提供無縫的雲端儲存與同步功能。

**實施細節：**

* **資料模型：** 將在 Firestore 中建立一個名為 business\_cards 的集合 (collection)。每個文件 (document) 代表一張名片，並透過其 Firebase Auth UID 與特定使用者關聯。文件結構將與 Kotlin 資料類別保持一致。
* **Firebase SDK 整合：** 將使用 gitliveapp/firebase-kotlin-sdk 從 commonMain 與 Firestore 進行互動 13。  
  CardRepository 將被擴展，以包含寫入和讀取 Firestore 的方法。
* 安全規則 20：  
  Firestore 安全規則對於保護使用者資料至關重要。規則將強制執行以下條件：
  * 使用者必須經過身份驗證才能讀取或寫入任何資料。
  * 使用者只能建立、讀取、更新或刪除 ownerUid 欄位與其自身 request.auth.uid 相符的文件。

JSON  
rules\_version \= '2';  
service cloud.firestore {  
match /databases/{database}/documents {  
match /business\_cards/{cardId} {  
allow read, update, delete: if request.auth\!= null && resource.data.ownerUid \== request.auth.uid;  
allow create: if request.auth\!= null && request.resource.data.ownerUid \== request.auth.uid;  
}  
}  
}

### **2.3 同步邏輯與衝突解決**

**目標：** 管理本地與雲端儲存之間的轉換，並處理資料衝突。

**同步流程：**

1. **升級：** 當使用者訂閱時，應用程式將觸發一次性的「上傳」流程，讀取本地 SQLDelight 資料庫中的所有名片，並將它們寫入該使用者的 Firestore 集合中。
2. **降級：** 如果訂閱失效，應用程式將恢復使用本地 SQLDelight 資料庫。雲端資料將保留在 Firestore 中，但除非使用者重新訂閱，否則不會進行主動同步。本地資料庫將再次成為事實來源。

衝突解決：「以後修改的為主 (Last Write Wins)」 21：

* **機制：** 根據規格要求，將採用此策略。Firestore 的伺服器端時間戳隱含地處理了此問題；對文件的最後一次寫入操作，無論是來自線上用戶端還是剛重新連線的離線用戶端，都將覆蓋之前的任何寫入 21。
* **實作：** 實作此策略無需特殊的用戶端程式碼。這是 Firestore 離線持久化功能在將資料同步回伺服器時的預設行為。
* **使用者體驗考量：** 雖然此策略簡單，但如果兩個裝置在其中一個離線時編輯同一張名片，可能會導致「更新遺失」的問題。UI 應提供清晰的同步狀態反饋，以減少使用者的困惑。

規格書中指定的「以後修改的為主」策略是一把雙面刃。其簡單性掩蓋了一個重大的使用者體驗風險。在多裝置情境下，使用者可能在手機上 (線上) 更新了一張名片，隨後在平板電腦上 (當時離線) 對同一張名片的 *舊版本* 進行了不同的編輯。當平板電腦重新連線時，其寫入操作將覆蓋手機上較新的編輯。從使用者的角度來看，這就是「資料遺失」。這是因為「最後」的寫入是由伺服器處理該操作的時間戳決定的，而非使用者執行操作的時間 21。因此，應用程式

*必須* 在 UI 中包含「同步中」和「已是最新」等狀態指示器，以管理使用者期望並防止挫折感。

此外，本地資料庫 SQLDelight 的角色不僅僅是免費版的功能，它也是付費版體驗的關鍵組成部分。它應作為 Firestore 資料的本地快取，為付費使用者提供完整的離線存取能力。CardRepository 應設計為實現快取旁路 (cache-aside) 模式：總是先從本地 SQLDelight 資料庫讀取。如果資料不存在，則從 Firestore 獲取，填充本地快取，然後返回資料。寫入操作應同時寫入 Firestore (用於同步) 和本地快取 (用於即時 UI 更新和離線可用性)。這為所有使用者創造了一個更具韌性且效能更佳的本地優先架構，同時也讓付費版的價值更為突顯。

---

## **第 3 節：使用者身份與商業化**

本節概述了使用者管理與營收生成的關鍵路徑，將 Firebase Authentication 與第三方應用程式內購買 (IAP) 供應商整合，以提供一個穩固且安全的解決方案。

### **3.1 使用者身份驗證**

**目標：** 實作一個標準且安全的電子郵件/密碼註冊與登入系統。

實施細節 24：

* **Firebase Authentication：** 將使用 gitliveapp/firebase-kotlin-sdk 的 firebase-auth 模組 13。
* **流程：** UI 將提供標準的註冊 (createUserWithEmailAndPassword) 與登入 (signInWithEmailAndPassword) 表單。ViewModel 將處理這些 API 呼叫，並根據成功或失敗更新 UI 狀態 (例如，導航至主畫面或顯示錯誤訊息)。
* **狀態管理：** 當前的身份驗證狀態 (登入/登出) 將透過觀察 Firebase.auth.authStateChanged 的 Flow 來實現，使應用程式能夠響應式地顯示適當的 UI (例如，登入畫面 vs. 名片列表)。

### **3.2 使用 RevenueCat 實作應用程式內購買**

**目標：** 實作一個可靠的跨平台訂閱系統，抽象化原生 IAP API 的複雜性。

**策略決策：** 雖然可以直接整合 Google Play Billing 27 和 Apple 的 StoreKit，但這需要大量的平台特定程式碼以及用於收據驗證的後端基礎設施。採用像 RevenueCat 這樣的第三方服務是業界的最佳實踐。它提供了 KMP SDK，處理購買 UI，並管理訂閱狀態，從而大幅減少開發工作量與風險 28。

實施細節 28：

* **SDK 設定：** 將 purchases-kmp-core 依賴項添加至 commonMain。將根據文件完成平台特定的設定，包括在 iOS 上透過 CocoaPods/SPM 連結 PurchasesHybridCommon 框架 28。
* **配置：** RevenueCat SDK 將在應用程式啟動時使用平台特定的公開 API 金鑰進行配置。Firebase Auth UID 將傳遞給 RevenueCat 的 logIn 方法，以將購買與使用者帳戶關聯 31。
* **購買流程：** 應用程式將從 RevenueCat 獲取可用的「產品 (Offerings)」以顯示訂閱頁面。當使用者點擊「訂閱」時，將呼叫 RevenueCat SDK 的 purchase() 方法。該 SDK 會處理整個平台原生的購買流程並返回結果。

### **3.3 後端收據驗證與權益管理**

**目標：** 為使用者的訂閱狀態建立一個安全的、由伺服器授權的單一事實來源。用戶端應用程式絕不應被信任其自身的狀態。

使用 RevenueCat Webhooks 與 Firebase Extension 實作 29：

* **設定：** 將在 Firebase 專案中安裝官方的 RevenueCat Firebase Extension。此擴充功能會自動部署一個作為 webhook 端點的 Cloud Function，並包含將訂閱資料寫入 Firestore 的邏輯 31。
* **Webhook 配置：** 在 RevenueCat 儀表板中，將配置一個 webhook 指向由擴充功能部署的 Cloud Function 的觸發 URL。將生成一個共享密鑰並在兩端進行配置，以確保通訊安全 32。
* **資料流：**
  1. 使用者在應用程式中透過 RevenueCat SDK 進行購買。
  2. RevenueCat 使用 Apple/Google 驗證收據並更新使用者狀態。
  3. RevenueCat 向 Firebase Function 發送一個已簽名的 webhook 事件 (例如 INITIAL\_PURCHASE、RENEWAL、CANCELLATION) 31。
  4. Firebase Function 驗證 webhook 的真實性，並將使用者更新後的權益狀態 (例如 isPremium: true、expiresAt: \<timestamp\>) 寫入 Firestore 中一個專用的 users 或 subscriptions 集合的文件中，該文件以使用者的 Firebase Auth UID 為鍵。
* **用戶端邏輯：** 應用程式將即時監聽 Firestore 中此使用者特定文件的變更。此文件是使用者付費狀態的 **單一事實來源**。當文件變更時，UI 會響應式地解鎖或鎖定付費功能。

透過使用 RevenueCat 和 Firebase webhook，我們實現了購買邏輯與權益邏輯的強力解耦。用戶端應用程式的唯一職責是 *發起購買*。它不需要知道購買 *是否* 成功或其當前狀態。後端 (RevenueCat \+ Firebase Function) 負責處理驗證和狀態變更。用戶端只需觀察 Firestore 中的結果。這使得用戶端應用程式更簡單、更安全 (因為它無法篡改自身狀態)，並且在購買過程中對網路故障更具韌性。

此外，將 RevenueCat 的使用者身份系統與 Firebase Auth (logIn(firebaseAuth.uid)) 整合，是實現無縫使用者體驗的關鍵。它確保使用者的權益與其永久帳戶綁定，而非匿名的裝置 ID。這意味著使用者可以在新裝置上登入，RevenueCat 將識別其 Firebase UID，其付費狀態將自動恢復，而無需手動點擊「恢復購買」按鈕。這是此架構「免費」帶來的重大使用者體驗改進。

### **3.4 功能實施矩陣 (免費版 vs. 付費版)**

此表格為所有利害關係人 (產品、工程、品保) 提供了一個一目了然的摘要，說明應用程式的行為和底層技術在兩個使用者層級之間的具體差異。它作為商業模型的簡潔技術轉譯。

| 功能 | 免費版實作 | 付費版實作 |
| :---- | :---- | :---- |
| **名片儲存** | 僅限本地 (SQLDelight 資料庫) | 雲端同步 (Cloud Firestore) 並搭配本地快取 (SQLDelight) |
| **儲存上限** | 10 張名片 (在 ViewModel 邏輯中於插入前強制執行) | 無限制 (用戶端或伺服器端均不強制執行限制) |
| **多裝置同步** | 不可用 | 透過 Cloud Firestore 即時更新啟用 |
| **資料存取** | 直接從本地 CardRepository (SQLDelight 實作) 讀寫 | 優先從本地快取讀取，寫入時同步至 Cloud Firestore 並更新本地快取 |
| **衝突解決** | 不適用 (單一資料來源) | 由 Cloud Firestore 管理的「以後修改的為主」策略 |
| **身份驗證** | 存取應用程式所需 | 存取應用程式及連結訂閱所需 |
| **訂閱管理** | 顯示「升級」按鈕，導向訂閱頁面 | 訂閱狀態透過 RevenueCat 管理，並從 Firestore 文件中反映 |

---

## **第 4 節：核心功能實作：AI 驅動的名片掃描**

本節詳細介紹應用程式的核心價值主張——自動化名片掃描功能的實作。從圖片擷取到 AI 辨識，再到資料儲存，整個流程將在此定義。

### **4.1 圖片擷取與選擇**

**目標：** 使用 expect/actual 模式實作原生的相機與照片圖庫選擇器。

實施細節 34：

* **expect 宣告 (commonMain)：** 在 commonMain 中定義一個 ImagePicker 介面或 expect 可組合函數。此宣告將定義一個統一的 API，用於啟動相機或圖庫，並透過回呼函數返回選定的圖片。  
  Kotlin  
  // commonMain  
  expect class ImagePicker(  
  onImageSelected: (ByteArray?) \-\> Unit  
  ) {  
  fun pickFromGallery()  
  fun takeFromCamera()  
  }

* **actual 實作 (androidMain)：**
  * 使用 rememberLauncherForActivityResult 搭配 ActivityResultContracts.GetContent() (圖庫) 和 ActivityResultContracts.TakePicture() (相機) 36。
  * 需要配置 FileProvider 以處理相機拍攝後圖片的 URI 36。
  * 在 AndroidManifest.xml 中宣告 CAMERA 權限。權限請求將透過 Accompanist Permissions 函式庫在 Composable 中處理。
* **actual 實作 (iosMain)：**
  * 使用 UIImagePickerController 來呈現原生的相機與圖庫介面 36。
  * 建立一個遵循 UIImagePickerControllerDelegateProtocol 和 UINavigationControllerDelegateProtocol 的委派物件，以處理使用者選擇的圖片或取消操作。
  * 在 Info.plist 中添加 NSCameraUsageDescription 和 NSPhotoLibraryUsageDescription 金鑰，以向使用者說明權限用途 36。

### **4.2 圖片預處理與上傳**

**目標：** 在上傳至後端函數前，於用戶端對圖片進行壓縮，以符合效能目標。

實施細節 37：

* **效能要求：** 規格書要求 AI 辨識在 8 秒內完成。這包括圖片上傳、AI 處理及結果回傳的時間。未經壓縮的高解析度圖片會顯著增加上傳延遲。
* **壓縮策略：** 在將圖片轉換為 ByteArray 上傳之前，將實施一個壓縮步驟。此壓縮邏輯將再次使用 expect/actual 模式。
  * **androidMain：** 使用 Bitmap.compress() 方法，將圖片編碼為 JPEG 或 WEBP 格式，並設定一個合理的品質參數 (例如 75-85)，以在檔案大小和視覺品質之間取得平衡 40。
  * **iosMain：** 使用 UIImageJPEGRepresentation() 或更新的 API，傳入一個壓縮品質因子 (0.0 至 1.0) 來縮小圖片檔案大小 38。
* **上傳：** 壓縮後的 ByteArray 將作為多部分表單資料 (multipart/form-data) 的一部分，透過 Ktor 或其他網路函式庫上傳至 Firebase Function。

### **4.3 Firebase AI Logic 與提示工程**

**目標：** 設計一個伺服器端的 Firebase Function，該函數接收圖片和可選的文字提示，並利用 Gemini 模型可靠地提取結構化的 JSON 資料。

**實施細節：**

* **後端服務：** 將部署一個 HTTP 觸發的 Firebase Cloud Function (第 2 代)。此函數將使用 Firebase AI Logic SDK (或直接使用 Vertex AI SDK) 來與 Gemini 模型互動 42。
* **提示工程 (Prompt Engineering)：** 提示的設計是此功能成敗的關鍵。一個精心設計的提示能夠引導模型產生準確且格式一致的輸出。
  * **多模態輸入：** 提示將包含圖片資料和使用者提供的可選文字。文字提示的目的是為了輔助模型，特別是在圖片中的文字模糊不清時，使用者可以手動輸入部分正確的資訊作為「線索」 45。
  * **角色設定：** 提示將以明確的指令開始，為模型設定角色，例如：「你是一個專業的名片掃描器。你的任務是從提供的圖片中準確提取聯絡資訊。」 46。
  * **結構化輸出要求：** 為了確保回傳的資料可以被程式化地解析，將利用 Gemini 的結構化輸出功能。這將透過在 API 請求中定義一個嚴格的 JSON 結構 (schema) 來實現 46。
  * **處理缺失資訊：** 提示將明確指示模型，如果名片上未找到某個欄位的資訊，應在 JSON 中回傳 null 或空字串，而不是省略該欄位 46。

### **4.4 AI 模型輸入/輸出合約**

此表格定義了與 Gemini API 互動的精確合約，確保了請求的一致性與回傳資料的可靠性。

| 合約部分 | 說明與範例 |
| :---- | :---- |
| **模型角色指令** | You are a business card information extraction specialist. Analyze the provided image and optional text hint to accurately extract contact details. |
| **輸入：圖片** | 圖片的 Base64 編碼字串或 Cloud Storage URI。 |
| **輸入：文字提示** | Extract the information from the business card. Pay close attention to the following hint if provided: "{user\_hint\_text}". (其中 {user\_hint\_text} 是使用者可選輸入的補充說明) |
| **輸出：JSON 結構 (Schema)** | json { "type": "object", "properties": { "name": {"type": "string"}, "company": {"type": "string"}, "title": {"type": "string"}, "phone": {"type": "string"}, "email": {"type": "string"}, "address": {"type": "string"}, "website": {"type": "string"} }, "required": \["name", "company", "title", "phone", "email", "address", "website"\] } |
| **處理缺失欄位的指令** | If a field cannot be found on the business card, return an empty string "" for that field's value. |

### **4.5 辨識至儲存的流程**

**目標：** 詳細說明從使用者發起掃描到最終儲存名片的完整 MVI 狀態流程。

**流程步驟：**

1. **使用者意圖 (Action)：** 使用者在名片列表頁點擊「+」按鈕，選擇圖片後，可選填補充說明，然後點擊「辨識」。UI 發送一個 RecognizeCard(image, hint) 的 ViewAction 至 ViewModel。
2. **狀態更新 (State)：** ViewModel 接收到 Action 後，立即將 ViewState 更新為 Loading("AI 智慧辨識中...")。UI 觀察到此狀態變化，顯示一個全螢幕的載入指示器 6。
3. 非同步作業： ViewModel 在其協程作用域內啟動一個非同步任務，該任務負責：  
   a. 壓縮圖片。  
   b. 將圖片和提示上傳至 Firebase Function。  
   c. 等待 JSON 回應。
4. 成功回傳：  
   a. 如果函數成功回傳結構化的 JSON，ViewModel 將解析該 JSON 並用其內容更新 ViewState 為 Editing(cardData)。  
   b. UI 觀察到 Editing 狀態，導航至名片編輯頁，並將 AI 辨識出的資料填入對應的輸入框中。
5. 失敗處理：  
   a. 如果 AI 辨識失敗 (例如，網路錯誤或後端函數回傳錯誤)，ViewModel 將 ViewState 更新為 RecognitionFailed。  
   b. UI 觀察到此狀態，顯示錯誤提示，並提供「重試」和「手動輸入」按鈕，如規格書所定義。
6. **儲存：** 使用者在編輯頁確認或修改資訊後，點擊「儲存」。UI 發送 SaveCard(cardData) 的 ViewAction。ViewModel 執行儲存操作 (寫入 SQLDelight 或 Firestore)，然後導航回名片列表頁。

---

## **第 5 節：使用者介面與體驗實作**

本節將詳細說明如何使用 Jetpack Compose 建構應用程式的使用者介面，並確保其能夠響應式地反映底層的狀態變化。

### **5.1 UI 元件設計**

**目標：** 使用 Jetpack Compose 建構規格書中定義的主要畫面。

**實施細節：**

* **登入/註冊頁：** 使用基礎的 Compose 元件，如 OutlinedTextField 用於輸入，Button 用於操作，以及 Image 用於顯示 Logo。
* **主畫面 (名片列表頁)：**
  * 使用 Scaffold 來佈局頂部應用程式列 (TopAppBar) 和右下角的浮動操作按鈕 (FloatingActionButton)。
  * 名片列表將使用 LazyColumn 實現，以高效地處理可能包含大量名片的滾動列表 50。每個列表項將是一個自訂的  
    @Composable 函數，用於顯示名片的摘要資訊。
* **訂閱頁：** 使用 Column、Text 和 Button 清晰地佈局付費版的優勢、價格和訂閱按鈕。
* **名片詳情/編輯頁：**
  * 頂部使用 AsyncImage (來自 Coil 或類似函式庫) 來顯示名片原圖。
  * 下方是一系列的 TextField 元件，用於顯示和編輯名片的各個欄位。

### **5.2 狀態驅動的 UI**

**目標：** 實作響應式 UI，使其能夠根據 ViewModel 的狀態變化自動更新，包括載入、空狀態和錯誤狀態。

實施細節 6：

* **狀態收集：** 每個畫面級別的 Composable 將從其對應的 ViewModel 接收一個 StateFlow\<ViewState\>。它將使用 collectAsState() 將 Flow 轉換為 Compose 的 State 物件。每當 ViewModel 更新其 ViewState 時，Compose 會自動觸發相關 Composable 的重組 (recomposition) 6。
* **條件式 UI 渲染：** 在 Composable 內部，將使用 when 運算式來根據 ViewState 的不同子類別渲染不同的 UI。  
  Kotlin  
  // 在 CardListScreen Composable 中  
  val state by viewModel.state.collectAsState()

  when (val viewState \= state) {  
  is CardListState.Loading \-\> {  
  // 顯示置中的 CircularProgressIndicator  
  }  
  is CardListState.Empty \-\> {  
  // 顯示空狀態畫面，包含歡迎訊息和引導圖示  
  }  
  is CardListState.Success \-\> {  
  // 顯示 LazyColumn 列表，資料來源為 viewState.cards  
  }  
  is CardListState.Error \-\> {  
  // 顯示錯誤訊息  
  }  
  }

* **載入畫面：** 如上所示，當狀態為 Loading 時，將渲染一個置中的 CircularProgressIndicator，並可附帶提示文字，例如在 AI 辨識期間顯示「AI 智慧辨識中...」。
* **空狀態畫面：** 當狀態為 Empty (例如，新使用者首次進入或所有名片都被刪除) 時，將顯示一個友好的歡迎訊息、圖示，並引導使用者點擊「+」按鈕新增第一張名片 51。

### **5.3 互動式元件**

**目標：** 實作可點擊的聯絡資訊，以啟動外部應用程式 (電話、郵件、瀏覽器)。

**實施細節：** 此功能需要與平台原生 API 互動，因此將再次使用 expect/actual 模式。

* **expect 宣告 (commonMain)：** 在 commonMain 中定義一個 UrlLauncher 介面或 expect 函數，用於處理不同類型的 URI。  
  Kotlin  
  // commonMain  
  expect fun launchUri(uri: String)

* actual 實作 (androidMain) 52：
  * 使用 Intent 來處理不同的 URI scheme。
  * https://...：建立一個帶有 Intent.ACTION\_VIEW 的 Intent。
  * tel:...：建立一個帶有 Intent.ACTION\_DIAL 的 Intent 54。
  * mailto:...：建立一個帶有 Intent.ACTION\_SENDTO 的 Intent。
  * 將使用 LocalContext.current 來獲取 Context 以啟動 Activity。
* actual 實作 (iosMain) 52：
  * 使用 UIApplication.sharedApplication.openURL() 來處理所有類型的 URI。
  * iOS 系統會根據 URI scheme (https://, tel:, mailto:) 自動選擇合適的應用程式來開啟。
  * 需要將字串轉換為 NSURL 物件。
* **在 Compose 中使用：** 在名片詳情頁中，電話、Email 和網址的 Text 元件將被包裹在一個 ClickableText 或帶有 clickable 修飾符的 Row/Column 中。點擊事件將觸發對 launchUri 函數的呼叫，並傳入對應的 URI 字串 (例如 "tel:${phoneNumber}")。

---

## **第 6 節：進階實作、安全性與效能**

本節將探討專案中更為複雜的技術挑戰，包括錯誤處理、事件管理、系統安全性及效能最佳化。

### **6.1 穩健的錯誤與事件處理**

**目標：** 在 MVI 架構內建立一個全面的策略，用於管理錯誤 (如網路故障) 和一次性事件 (如操作成功後的通知)，以確保流暢的使用者體驗。

**實施細節：**

* **錯誤狀態建模：** ViewState 將包含一個可為空的 error 屬性。當發生可恢復的錯誤時 (例如，載入列表失敗)，ViewModel 會將錯誤訊息填入此屬性，UI 則會顯示相應的提示。
* 一次性事件處理 (One-off Events) 9：
  * **挑戰：** 像「儲存成功」的 Snackbar 通知或導航事件，不應被視為 UI 的持久狀態。如果將它們建模為 ViewState 的一部分，當發生設定變更 (如螢幕旋轉) 導致 UI 重組時，這些事件可能會被錯誤地再次觸發 56。
  * **解決方案：** 將使用 Kotlin Channel 來處理這些一次性事件。ViewModel 將維護一個 Channel\<ViewEvent\>，並將其作為 Flow 暴露給 UI。
  * 流程 9：
    1. ViewModel 在完成某項操作後 (例如，成功儲存名片)，會向 Channel 發送一個事件，例如 send(ShowSaveSuccessSnackbar)。
    2. UI 層使用 LaunchedEffect 來收集 (collect) 這個 Flow。LaunchedEffect 的生命週期與 Composable 綁定，確保事件只在 UI 處於活躍狀態時被處理一次 57。
    3. 在 LaunchedEffect 區塊內，when 運算式會處理接收到的事件，例如呼叫 scaffoldState.snackbarHostState.showSnackbar(...)。
  * 這種方法將短暫的「事件」與持久的「狀態」明確分離，是 MVI 架構中處理此類問題的標準最佳實踐 55。

### **6.2 安全性態勢**

**目標：** 確保應用程式的後端資源和使用者資料得到充分保護。

**實施細節：**

* **Firestore 安全規則：** 如第 2.2 節所述，將實作嚴格的、基於使用者身份的存取控制規則，確保使用者只能存取自己的資料 20。
* **保護 Firebase Function：**
  * AI 辨識的 Cloud Function 是一個潛在的濫用目標。將使用 Firebase App Check 來保護此端點。App Check 會驗證請求是否來自合法的、未被篡改的應用程式實例，從而有效阻止機器人、模擬器和未經授權的用戶端的存取。
  * 此外，函數內部應驗證呼叫者的身份驗證令牌，確保只有已登入的使用者才能觸發辨識過程。
* **傳輸加密：** 所有用戶端與 Firebase 服務之間的網路通訊都將預設使用 HTTPS/TLS 進行加密。
* **儲存加密：** 儲存在 Firebase 上的所有資料，包括 Firestore 和 Cloud Storage 中的圖片，都將利用 Google Cloud Platform 提供的標準伺服器端加密進行靜態加密。

### **6.3 效能調校**

**目標：** 滿足非功能性需求，特別是將 AI 辨識延遲控制在 8 秒以內。

**實施細節：**

* **AI 辨識速度：**
  * **用戶端最佳化：** 如第 4.2 節所述，上傳前的圖片壓縮是降低延遲的最關鍵步驟。較小的檔案尺寸意味著更短的上傳時間。
  * **後端最佳化：** Firebase Function 的「冷啟動」是延遲的主要來源。為了緩解此問題，可以為該函數設定最小實例數 (min instances)。將此值設定為 1 或更高，可以確保總有一個實例處於「暖」狀態，隨時準備處理請求，從而顯著減少首次呼叫的延遲 59。
* **UI 效能：**
  * 在 Compose 中，將遵循最佳實踐，例如為 LazyColumn 的項目提供穩定的鍵 (key)，以避免在資料變動時不必要的重組 50。
  * 避免在 Composable 函數中進行複雜的計算，應將這些計算移至 ViewModel 或更低的層級。

---

## **第 7 節：結論與策略建議**

### **7.1 藍圖綜合**

本藍圖為「名片助理」應用程式提供了一個全面且技術先進的架構設計。透過結合 Compose Multiplatform 的跨平台 UI 能力、MVI 與 Decompose 的穩健架構、Firebase 全家桶的強大後端服務，以及 SQLDelight 的高效本地持久化，我們構建了一個可擴展、可維護且具備現代化使用者體驗的系統。

此架構的核心優勢在於其高度的程式碼共享率和明確的關注點分離。業務邏輯、UI 和資料模型被集中在 commonMain 中，而平台特定的實作則被有效地隔離在 actual 宣告中。採用 RevenueCat 處理應用程式內購買，以及利用 Firebase AI Logic 進行智慧辨識，不僅加速了開發進程，也降低了實作複雜性和相關風險。

### **7.2 測試與部署建議**

**測試策略：**

* **單元測試：** 由於 MVI 架構將業務邏輯集中在 ViewModel 中，因此可以對 ViewModel 進行全面的單元測試。測試將驗證在給定一個 ViewAction 後，ViewModel 是否能正確地轉換到預期的 ViewState 或發出正確的 ViewEvent。
* **UI 測試：** Compose 提供了強大的測試框架。可以為各個 Composable 元件以及完整的畫面編寫 UI 測試，以驗證 UI 是否能根據不同的 ViewState 正確渲染。
* **整合測試：** 應編寫測試來驗證倉儲層 (Repository) 與 SQLDelight 和 Firestore 的互動是否正確。Firebase 提供了本地模擬器套件 (Local Emulator Suite)，可以在本地環境中測試 Firestore 的規則和函數邏輯，而無需實際的網路呼叫 25。

**部署考量：**

* **持續整合/持續部署 (CI/CD)：** 建議建立一個 CI/CD 流程 (例如使用 GitHub Actions)，以自動化建置、測試和部署 Android (AAB) 與 iOS (IPA) 的應用程式套件。
* **版本管理：** 應採用語意化版本控制 (Semantic Versioning) 來管理應用程式的版本。
* **App Store 審核：**
  * **iOS：** 需確保所有與權限相關的 Info.plist 金鑰都已正確填寫。應用程式內購買的項目需要在 App Store Connect 中預先設定和審核。
  * **Android：** 需在 Google Play Console 中設定應用程式內購買的商品，並確保應用程式符合所有最新的 Play 商店政策。
* **監控：** 應用程式發布後，應使用 Firebase Performance Monitoring 和 Crashlytics 來監控應用程式的效能和穩定性，以便及時發現並解決問題。

#### **引用的著作**

1. Create your Compose Multiplatform app \- JetBrains, 檢索日期：8月 29, 2025， [https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-create-first-app.html](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-create-first-app.html)
2. Compose Multiplatform: Getting Started & Initializing Firebase | by Eury Pérez Beltré, 檢索日期：8月 29, 2025， [https://euryperez.dev/compose-multiplatform-getting-started-initializing-firebase-5abd99a1ae91](https://euryperez.dev/compose-multiplatform-getting-started-initializing-firebase-5abd99a1ae91)
3. Learn How To Create Shared KMP Navigation With Decompose \- Stackademic, 檢索日期：8月 29, 2025， [https://blog.stackademic.com/learn-how-to-create-shared-kmp-navigation-with-decompose-3cd72ca8c0b7](https://blog.stackademic.com/learn-how-to-create-shared-kmp-navigation-with-decompose-3cd72ca8c0b7)
4. Create a multiplatform app using Ktor and SQLDelight | Kotlin ..., 檢索日期：8月 29, 2025， [https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-ktor-sqldelight.html](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-ktor-sqldelight.html)
5. Creating an MVI architecture in Compose Multiplatform \- avwie's programming blog, 檢索日期：8月 29, 2025， [https://avwie.github.io/mvi-architecture-in-compose-multiplatform/](https://avwie.github.io/mvi-architecture-in-compose-multiplatform/)
6. How Can MVI Architecture Revolutionize Your Compose Multiplatform Development? | by Akbar Dzulfikar | Medium, 檢索日期：8月 29, 2025， [https://medium.com/@akbar.dzulfikar/how-can-mvi-architecture-revolutionize-your-compose-multiplatform-development-e3a9812a7aee](https://medium.com/@akbar.dzulfikar/how-can-mvi-architecture-revolutionize-your-compose-multiplatform-development-e3a9812a7aee)
7. Mastering MVI Architecture in Android: A Comprehensive Guide \- Stackademic, 檢索日期：8月 29, 2025， [https://blog.stackademic.com/mastering-mvi-architecture-in-android-a-comprehensive-guide-9a2342a03a83](https://blog.stackademic.com/mastering-mvi-architecture-in-android-a-comprehensive-guide-9a2342a03a83)
8. State and Jetpack Compose | Android Developers, 檢索日期：8月 29, 2025， [https://developer.android.com/develop/ui/compose/state](https://developer.android.com/develop/ui/compose/state)
9. MVI with Android Compose on a real example | by Alex Zaitsev ..., 檢索日期：8月 29, 2025， [https://medium.com/@alexzaitsev/mvi-with-android-compose-on-a-real-example-f5d522707be5](https://medium.com/@alexzaitsev/mvi-with-android-compose-on-a-real-example-f5d522707be5)
10. Do-It-Yourself Compose Multiplatform Navigation with Decompose ..., 檢索日期：8月 29, 2025， [https://proandroiddev.com/do-it-yourself-compose-multiplatform-navigation-with-decompose-94ac8126e6b5](https://proandroiddev.com/do-it-yourself-compose-multiplatform-navigation-with-decompose-94ac8126e6b5)
11. Supported platforms, frameworks, libraries, and tools \- Firebase \- Google, 檢索日期：8月 29, 2025， [https://firebase.google.com/docs/libraries](https://firebase.google.com/docs/libraries)
12. Does Firebase team plan to support Kotlin Multiplatform Mobile? \- Reddit, 檢索日期：8月 29, 2025， [https://www.reddit.com/r/Firebase/comments/jge2ks/does\_firebase\_team\_plan\_to\_support\_kotlin/](https://www.reddit.com/r/Firebase/comments/jge2ks/does_firebase_team_plan_to_support_kotlin/)
13. GitLiveApp/firebase-kotlin-sdk: A Kotlin-first SDK for Firebase \- GitHub, 檢索日期：8月 29, 2025， [https://github.com/GitLiveApp/firebase-kotlin-sdk](https://github.com/GitLiveApp/firebase-kotlin-sdk)
14. Hello Community I started my KMP project earlier this week a kotlinlang \#multiplatform, 檢索日期：8月 29, 2025， [https://slack-chats.kotlinlang.org/t/27095127/hello-community-i-started-my-kmp-project-earlier-this-week-a](https://slack-chats.kotlinlang.org/t/27095127/hello-community-i-started-my-kmp-project-earlier-this-week-a)
15. Unlocking Firebase in a Kotlin Multiplatform App \- droidcon, 檢索日期：8月 29, 2025， [https://www.droidcon.com/2025/06/13/unlocking-firebase-in-a-kotlin-multiplatform-app/](https://www.droidcon.com/2025/06/13/unlocking-firebase-in-a-kotlin-multiplatform-app/)
16. Unlocking Firebase in a Kotlin Multiplatform App | by Kevin Block | ProAndroidDev, 檢索日期：8月 29, 2025， [https://proandroiddev.com/unlocking-firebase-in-a-kotlin-multiplatform-app-9e510a7c71c3](https://proandroiddev.com/unlocking-firebase-in-a-kotlin-multiplatform-app-9e510a7c71c3)
17. KMPAuth — Kotlin Multiplatform Authentication Library \- Mirzamehdi Karimov \- Medium, 檢索日期：8月 29, 2025， [https://medium.com/@mirzemehdi/kmpauth-kotlin-multiplatform-authentication-library-a6d23fd83cb5](https://medium.com/@mirzemehdi/kmpauth-kotlin-multiplatform-authentication-library-a6d23fd83cb5)
18. SqlDelight Database in Kotlin Multiplatform | by Kaushal Vasava \- Stackademic, 檢索日期：8月 29, 2025， [https://blog.stackademic.com/sqldelight-database-in-kotlin-multiplatform-b30b23e81cd7](https://blog.stackademic.com/sqldelight-database-in-kotlin-multiplatform-b30b23e81cd7)
19. SQLDelight \- Generates typesafe Kotlin APIs from SQL \- GitHub, 檢索日期：8月 29, 2025， [https://github.com/sqldelight/sqldelight](https://github.com/sqldelight/sqldelight)
20. Secure data in Cloud Firestore \- Firebase, 檢索日期：8月 29, 2025， [https://firebase.google.com/docs/firestore/security/overview](https://firebase.google.com/docs/firestore/security/overview)
21. Cloud Firestore has support for transactions: https://firebase.google.com/docs/f... | Hacker News, 檢索日期：8月 29, 2025， [https://news.ycombinator.com/item?id=15394050](https://news.ycombinator.com/item?id=15394050)
22. What is Last Write Wins? \- database \- Stack Overflow, 檢索日期：8月 29, 2025， [https://stackoverflow.com/questions/26890288/what-is-last-write-wins](https://stackoverflow.com/questions/26890288/what-is-last-write-wins)
23. Understand reads and writes at scale | Firestore \- Firebase \- Google, 檢索日期：8月 29, 2025， [https://firebase.google.com/docs/firestore/understand-reads-writes-scale](https://firebase.google.com/docs/firestore/understand-reads-writes-scale)
24. Firebase Authentication | Simple, multi-platform sign-in, 檢索日期：8月 29, 2025， [https://firebase.google.com/products/auth](https://firebase.google.com/products/auth)
25. Get Started with Firebase Authentication on Android, 檢索日期：8月 29, 2025， [https://firebase.google.com/docs/auth/android/start](https://firebase.google.com/docs/auth/android/start)
26. How to implement Google authenticate with Firebase using Compose Multiplatform, 檢索日期：8月 29, 2025， [https://stackoverflow.com/questions/79411720/how-to-implement-google-authenticate-with-firebase-using-compose-multiplatform](https://stackoverflow.com/questions/79411720/how-to-implement-google-authenticate-with-firebase-using-compose-multiplatform)
27. Integrate the Google Play Billing Library into your app \- Android Developers, 檢索日期：8月 29, 2025， [https://developer.android.com/google/play/billing/integrate](https://developer.android.com/google/play/billing/integrate)
28. Kotlin Multiplatform | In-App Subscriptions Made Easy – RevenueCat, 檢索日期：8月 29, 2025， [https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform](https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform)
29. In-App Purchases with Firebase \- RevenueCat, 檢索日期：8月 29, 2025， [https://www.revenuecat.com/firebase/](https://www.revenuecat.com/firebase/)
30. Implementation Responsibilities | In-App Subscriptions Made Easy \- RevenueCat, 檢索日期：8月 29, 2025， [https://www.revenuecat.com/docs/platform-resources/implementation-responsibilities](https://www.revenuecat.com/docs/platform-resources/implementation-responsibilities)
31. Firebase | In-App Subscriptions Made Easy \- RevenueCat, 檢索日期：8月 29, 2025， [https://www.revenuecat.com/docs/integrations/third-party-integrations/firebase-integration](https://www.revenuecat.com/docs/integrations/third-party-integrations/firebase-integration)
32. Webhooks | In-App Subscriptions Made Easy – RevenueCat, 檢索日期：8月 29, 2025， [https://www.revenuecat.com/docs/integrations/webhooks](https://www.revenuecat.com/docs/integrations/webhooks)
33. RevenueCat Webhook Auth? : r/Firebase \- Reddit, 檢索日期：8月 29, 2025， [https://www.reddit.com/r/Firebase/comments/sdiqj6/revenuecat\_webhook\_auth/](https://www.reddit.com/r/Firebase/comments/sdiqj6/revenuecat_webhook_auth/)
34. Compose Multi-Platform \+ Custom Camera With Common Capture Design \- ProAndroidDev, 檢索日期：8月 29, 2025， [https://proandroiddev.com/compose-multi-platform-custom-camera-with-common-capture-design-386dbc2aa03e](https://proandroiddev.com/compose-multi-platform-custom-camera-with-common-capture-design-386dbc2aa03e)
35. Kotlin Multiplatform \+ Compose: Unified Camera & Gallery Picker with Expect/Actual and Permission Handling | by Ismoy Belizaire | Aug, 2025 | Medium, 檢索日期：8月 29, 2025， [https://medium.com/@belizairesmoy72/kotlin-multiplatform-compose-unified-camera-gallery-picker-with-expect-actual-and-permission-d3a527f649f1](https://medium.com/@belizairesmoy72/kotlin-multiplatform-compose-unified-camera-gallery-picker-with-expect-actual-and-permission-d3a527f649f1)
36. Kotlin Multiplatform Compose: Unified Image Capture and Gallery ..., 檢索日期：8月 29, 2025， [https://medium.com/@qasimnawaz\_70901/kotlin-multiplatform-compose-unified-image-capture-and-gallery-picker-with-permission-handling-8a8f8cc9cc82](https://medium.com/@qasimnawaz_70901/kotlin-multiplatform-compose-unified-image-capture-and-gallery-picker-with-permission-handling-8a8f8cc9cc82)
37. How to Compress Image in Android Before Uploading it to Firebase Storage?, 檢索日期：8月 29, 2025， [https://www.geeksforgeeks.org/android/how-to-compress-image-in-android-before-uploading-it-to-firebase-storage/](https://www.geeksforgeeks.org/android/how-to-compress-image-in-android-before-uploading-it-to-firebase-storage/)
38. Uploading in Compose Multiplatform Using Ktor: A Comprehensive Guide \- Medium, 檢索日期：8月 29, 2025， [https://medium.com/@razaghimahdi78/uploading-in-compose-multiplatform-using-ktor-a-comprehensive-guide-f2dd28799a20](https://medium.com/@razaghimahdi78/uploading-in-compose-multiplatform-using-ktor-a-comprehensive-guide-f2dd28799a20)
39. Reducing image download sizes | Views \- Android Developers, 檢索日期：8月 29, 2025， [https://developer.android.com/develop/ui/views/graphics/reduce-image-sizes](https://developer.android.com/develop/ui/views/graphics/reduce-image-sizes)
40. I m trying to upload an Image from a Compose Multiplatform v kotlinlang \#compose, 檢索日期：8月 29, 2025， [https://slack-chats.kotlinlang.org/t/16322048/i-m-trying-to-upload-an-image-from-a-compose-multiplatform-v](https://slack-chats.kotlinlang.org/t/16322048/i-m-trying-to-upload-an-image-from-a-compose-multiplatform-v)
41. How to Implement Image Compression On Android \- YouTube, 檢索日期：8月 29, 2025， [https://www.youtube.com/watch?v=Q0Njj-rfEXE\&pp=0gcJCfwAo7VqN5tD](https://www.youtube.com/watch?v=Q0Njj-rfEXE&pp=0gcJCfwAo7VqN5tD)
42. Generative AI | Build AI-powered apps faster with Firebase, 檢索日期：8月 29, 2025， [https://firebase.google.com/products/generative-ai](https://firebase.google.com/products/generative-ai)
43. Get Started with Firebase AI Logic on Android \- YouTube, 檢索日期：8月 29, 2025， [https://www.youtube.com/watch?v=0UoReIOwC-Q](https://www.youtube.com/watch?v=0UoReIOwC-Q)
44. Hybrid AI prompting with Firebase AI Logic \- Chrome for Developers, 檢索日期：8月 29, 2025， [https://developer.chrome.com/docs/ai/firebase-ai-logic](https://developer.chrome.com/docs/ai/firebase-ai-logic)
45. Text generation | Gemini API | Google AI for Developers, 檢索日期：8月 29, 2025， [https://ai.google.dev/gemini-api/docs/text-generation](https://ai.google.dev/gemini-api/docs/text-generation)
46. Prompt design strategies | Gemini API | Google AI for Developers, 檢索日期：8月 29, 2025， [https://ai.google.dev/gemini-api/docs/prompting-strategies](https://ai.google.dev/gemini-api/docs/prompting-strategies)
47. Generate text using the Gemini API | Firebase AI Logic \- Google, 檢索日期：8月 29, 2025， [https://firebase.google.com/docs/ai-logic/generate-text](https://firebase.google.com/docs/ai-logic/generate-text)
48. Structured output | Gemini API | Google AI for Developers, 檢索日期：8月 29, 2025， [https://ai.google.dev/gemini-api/docs/structured-output](https://ai.google.dev/gemini-api/docs/structured-output)
49. How to consistently output JSON with the Gemini API using controlled generation \- Medium, 檢索日期：8月 29, 2025， [https://medium.com/google-cloud/how-to-consistently-output-json-with-the-gemini-api-using-controlled-generation-887220525ae0](https://medium.com/google-cloud/how-to-consistently-output-json-with-the-gemini-api-using-controlled-generation-887220525ae0)
50. Lists and grids | Jetpack Compose \- Android Developers, 檢索日期：8月 29, 2025， [https://developer.android.com/develop/ui/compose/lists](https://developer.android.com/develop/ui/compose/lists)
51. How do you manage empty states? \#Kotlin \#Android | by Mklstudio \- Medium, 檢索日期：8月 29, 2025， [https://medium.com/@mklstudio80/how-do-you-manage-empty-states-kotlin-android-08813126fa9e](https://medium.com/@mklstudio80/how-do-you-manage-empty-states-kotlin-android-08813126fa9e)
52. Bridging the Platform Divide: Mastering Expect/Actual Logic in ..., 檢索日期：8月 29, 2025， [https://medium.com/@saeednoshadi89/bridging-the-platform-divide-mastering-expect-actual-logic-in-compose-multiplatform-428bdeb4a314](https://medium.com/@saeednoshadi89/bridging-the-platform-divide-mastering-expect-actual-logic-in-compose-multiplatform-428bdeb4a314)
53. Building & Deploying a simple KMP app — Part 3: Launchers | by Robert Munro, 檢索日期：8月 29, 2025， [https://proandroiddev.com/building-deploying-a-simple-kmp-app-part-3-launchers-ff8ec2c60d06](https://proandroiddev.com/building-deploying-a-simple-kmp-app-part-3-launchers-ff8ec2c60d06)
54. Android \- Open Dialer Through Intent using Jetpack Compose \- GeeksforGeeks, 檢索日期：8月 29, 2025， [https://www.geeksforgeeks.org/kotlin/android-open-dialer-through-intent-using-jetpack-compose/](https://www.geeksforgeeks.org/kotlin/android-open-dialer-through-intent-using-jetpack-compose/)
55. How to Handle Errors in Jetpack Compose with MVI and Clean Architecture \- Medium, 檢索日期：8月 29, 2025， [https://medium.com/@razaghimahdi78/how-to-handle-errors-in-jetpack-compose-with-mvi-and-clean-architecture-55ab17b9c82d](https://medium.com/@razaghimahdi78/how-to-handle-errors-in-jetpack-compose-with-mvi-and-clean-architecture-55ab17b9c82d)
56. Android One-off Events: Approaches, evolution & anti-patterns | by Eury Pérez Beltré, 檢索日期：8月 29, 2025， [https://proandroiddev.com/android-one-off-events-approaches-evolution-anti-patterns-add887cd0250](https://proandroiddev.com/android-one-off-events-approaches-evolution-anti-patterns-add887cd0250)
57. Side-effects in Compose \- Android Developers, 檢索日期：8月 29, 2025， [https://developer.android.com/develop/ui/compose/side-effects](https://developer.android.com/develop/ui/compose/side-effects)
58. How do you handle one-shot events like navigation decisions inside Compose? \- Reddit, 檢索日期：8月 29, 2025， [https://www.reddit.com/r/androiddev/comments/15yyjze/how\_do\_you\_handle\_oneshot\_events\_like\_navigation/](https://www.reddit.com/r/androiddev/comments/15yyjze/how_do_you_handle_oneshot_events_like_navigation/)
59. Functions best practices | Cloud Run Documentation, 檢索日期：8月 29, 2025， [https://cloud.google.com/run/docs/tips/functions-best-practices](https://cloud.google.com/run/docs/tips/functions-best-practices)
