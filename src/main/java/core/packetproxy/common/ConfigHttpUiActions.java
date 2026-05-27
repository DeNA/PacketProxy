package packetproxy.common;

/** リモート設定 HTTP API から必要な GUI 操作。 */
public interface ConfigHttpUiActions {

	void showOptionsTab();

	/**
	 * @return 設定上書きを許可する場合 true
	 */
	boolean confirmOverwriteConfig();
}
