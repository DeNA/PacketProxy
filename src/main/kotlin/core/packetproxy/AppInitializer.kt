package packetproxy

import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.system.exitProcess
import packetproxy.common.ClientKeyManager
import packetproxy.common.ConfigIO
import packetproxy.common.Utils
import packetproxy.model.Database
import packetproxy.model.Packets
import packetproxy.util.Logging

object AppInitializer {
  private var isGulp = false // Gulp modeか否か
  private var settingsPath = "" // 設定用JSONのファイルpath

  private var isCoreNotReady = true
  private var isGulpNotReady = true
  private var isComponentsNotReady = true

  @JvmStatic
  fun setArgs(isGulp: Boolean, settingsPath: String?) {
    this.isGulp = isGulp
    this.settingsPath = settingsPath ?: ""
  }

  /** GUI / CLI(Gulp) に関連なく最初に実行するべき初期化を一度のみ実行する */
  @JvmStatic
  fun initCore() {
    check(isCoreNotReady) { "initCore() has already been done !" }

    // ログ機能のエラーについては標準エラー出力への出力を行い終了する
    try {
      Logging.init(isGulp)
    } catch (e: Exception) {
      System.err.println("[FATAL ERROR]: Logging.init(), exit 1")
      System.err.println(e.message)
      e.printStackTrace(System.err)

      exitProcess(1)
    }

    Logging.log("Launching PacketProxy !")

    isCoreNotReady = false
  }

  /** CLI(Gulp) 専用の初期化を実行 GUI ではGUIMainなどで実行されている処理 */
  @JvmStatic
  fun initGulp() {
    check(isGulp) { "initGulp() is for gulp mode only !" }
    check(isGulpNotReady) { "initGulp() has already been done !" }

    initDatabase()
    initPackets()

    isGulpNotReady = false
  }

  private fun initDatabase() {
    val dbPath =
      Paths.get(System.getProperty("user.home"), ".packetproxy", "db", "resources.sqlite3")
    Database.getInstance().openAt(dbPath.toString())
    Logging.log("Databaseを初期化しました: $dbPath")
  }

  private fun initPackets() {
    Packets.getInstance(false) // CLIモードでは履歴を復元しない
    Logging.log("Packetsを初期化しました")
  }

  /**
   * GUI / CLI(Gulp) に共通の初期化を GUI の表示よりも後回しして良い初期化を一度のみ実行する
   *
   * 並列処理による高速化:
   * - EncoderManagerとVulCheckerManagerは完全に独立しているため、並列実行可能
   * - ClientKeyManagerとListenPortManagerはDatabaseに依存しているが、
   *   Databaseは既に初期化済み（GUIモードではstartGUI()で、CLIモードではinitGulp()で初期化）
   *   かつ、それぞれ異なるテーブル（ClientCertificates/Servers/ListenPorts）にアクセスするため、 読み取り操作のみであれば並列実行可能
   *
   * 依存関係の整理:
   * 1. ClientKeyManager: ClientCertificates → Database (読み取りのみ)
   * 2. ListenPortManager: ListenPorts + Servers → Database (読み取りのみ)
   * 3. EncoderManager: クラスパス/JARファイルのスキャン（Database非依存）
   * 4. VulCheckerManager: クラスパスのスキャン（Database非依存）
   */
  @JvmStatic
  fun initComponents() {
    check(isComponentsNotReady) { "initComponents() has already been done !" }

    // Database依存のコンポーネントを並列実行
    // 注意: Databaseは既に初期化済みであることを前提とする
    val dbDependentFuture1 = CompletableFuture.runAsync { initClientKeyManager() }

    val dbDependentFuture2 = CompletableFuture.runAsync { initListenPortManager() }

    // Database非依存のコンポーネントを並列実行
    val independentFuture1 =
      CompletableFuture.runAsync {
        // encoderのロードに1,2秒かかるのでここでロードをしておく（ここでしておかないと通信がacceptされたタイミングでロードする）
        initEncoderManager()
      }

    val independentFuture2 = CompletableFuture.runAsync { initVulCheckerManager() }

    // 全ての初期化が完了するまで待機
    try {
      CompletableFuture.allOf(
          dbDependentFuture1,
          dbDependentFuture2,
          independentFuture1,
          independentFuture2,
        )
        .get()

      Logging.log("全てのコンポーネントの初期化が完了しました")
    } catch (e: ExecutionException) {
      // ExecutionExceptionは、CompletableFuture内で発生した例外をラップした例外
      // e.causeで実際の例外を取得できる
      val cause = e.cause
      if (cause is Exception) {
        Logging.errWithStackTrace(cause)
        throw cause
      } else {
        Logging.errWithStackTrace(e)
        throw e
      }
    } catch (e: InterruptedException) {
      Logging.errWithStackTrace(e)
      Thread.currentThread().interrupt()
      throw RuntimeException("初期化が中断されました", e)
    }

    loadSettingsFromJson()

    isComponentsNotReady = false
  }

  private fun initClientKeyManager() {
    ClientKeyManager.initialize()
    Logging.log("ClientKeyManagerを初期化しました")
  }

  private fun initListenPortManager() {
    ListenPortManager.getInstance()
    Logging.log("ListenPortManagerを初期化しました")
  }

  private fun initEncoderManager() {
    EncoderManager.getInstance()
    Logging.log("EncoderManagerを初期化しました")
  }

  private fun initVulCheckerManager() {
    VulCheckerManager.getInstance()
    Logging.log("VulCheckerManagerを初期化しました")
  }

  /** JSON設定ファイルを読み込んで適用 ListenPortManager初期化後に呼び出すことで、設定ファイル内の有効なプロキシが自動的に開始される */
  private fun loadSettingsFromJson() {
    if (settingsPath.isEmpty()) return

    try {
      val jsonBytes = Utils.readfile(settingsPath)
      val json = String(jsonBytes, Charsets.UTF_8)

      val configIO = ConfigIO()
      configIO.setOptions(json)

      Logging.log("設定ファイルを正常に読み込みました: $settingsPath")
    } catch (e: Exception) {
      Logging.err("設定ファイルの読み込みに失敗しました: ${e.message}", e)
      Logging.errWithStackTrace(e)
    }
  }
}
