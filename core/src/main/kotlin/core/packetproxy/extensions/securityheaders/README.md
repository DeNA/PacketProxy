# Security Headers Extension

PacketProxy用のセキュリティヘッダー分析拡張機能です。HTTPレスポンスヘッダーを分析し、セキュリティ上の問題（CSPの欠如、HSTSの不備など）を検出します。

## ルールの追加方法

新しいセキュリティチェックルールを追加するには、以下の手順に従ってください。

### 1. SecurityCheckインターフェースの実装

`packetproxy.extensions.securityheaders.checks` パッケージ内に、`SecurityCheck` インターフェースを実装した新しいクラスを作成します。

実装が必要なメソッド・プロパティ：

* **name**: チェックの表示名（Issuesタブで使用）
* **columnName**: 結果テーブルのカラム名
* **failMessage**: チェック失敗時のエラーメッセージ
* **warnMessage**: チェック警告時の警告メッセージ（オプション、デフォルトでは`failMessage`が使用される）
* **matchesHeaderLine(headerLine: String)**: このチェックが対象とするヘッダー行かどうかを判定（小文字で判定）
* **check(header: HttpHeader, context: MutableMap<String, Any>)**: チェック処理の本体
  * 戻り値として `SecurityCheckResult.ok()`, `.warn()`, `.fail()` を返します。

オプションで以下のメソッド・プロパティをオーバーライドして、結果表示のハイライトをカスタマイズできます：

* **greenPatterns**: 安全な設定を示す文字列パターン
* **yellowPatterns**: 注意が必要な設定を示す文字列パターン
* **redPatterns**: 危険な設定を示す文字列パターン

実装例：

```kotlin
package packetproxy.extensions.securityheaders.checks

import packetproxy.extensions.securityheaders.SecurityCheck
import packetproxy.extensions.securityheaders.SecurityCheckResult
import packetproxy.http.HttpHeader

class MyCustomCheck : SecurityCheck {

    override val name: String = "My Check"
    override val columnName: String = "MyCheck"
    override val failMessage: String = "My Check header is missing"
    override val warnMessage: String = "My Check header has potential issues"

    override fun matchesHeaderLine(headerLine: String): Boolean {
        return headerLine.startsWith("my-header:")
    }

    override fun check(header: HttpHeader, context: MutableMap<String, Any>): SecurityCheckResult {
        val value = header.getValue("My-Header").orElse("")
        return when {
            value == "secure-value" -> SecurityCheckResult.ok(value, value)
            value == "insecure-value" -> SecurityCheckResult.fail("Invalid value", value)
            value.isNotEmpty() -> SecurityCheckResult.warn(value, value)
            else -> SecurityCheckResult.fail("Missing header", "")
        }
    }
}
```

### 2. ルールの登録

`packetproxy.extensions.securityheaders.SecurityHeadersExtension` クラスの `SECURITY_CHECKS` リストに、作成したクラスのインスタンスを追加します。

```kotlin
private val SECURITY_CHECKS =
  listOf(
    CspCheck(),
    XssProtectionCheck(),
    // ...
    MyCustomCheck(), 
  )
```

### 3. ビルドと実行

プロジェクトを再ビルドし、PacketProxyを起動すると、新しいカラムが追加され、チェックが実行されます。
