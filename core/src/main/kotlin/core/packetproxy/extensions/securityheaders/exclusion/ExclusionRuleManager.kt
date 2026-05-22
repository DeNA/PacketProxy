/*
 * Copyright 2026 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy.extensions.securityheaders.exclusion

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/** Manages exclusion rules for security header checks. Thread-safe singleton implementation. */
object ExclusionRuleManager {
  private val rules = CopyOnWriteArrayList<ExclusionRule>()
  private val listeners = CopyOnWriteArrayList<Consumer<List<ExclusionRule>>>()

  fun addRule(rule: ExclusionRule) {
    rules.add(rule)
    notifyListeners()
  }

  fun removeRule(ruleId: String) {
    rules.removeIf { it.id == ruleId }
    notifyListeners()
  }

  fun updateRule(ruleId: String, newType: ExclusionRuleType, newPattern: String) {
    val index = rules.indexOfFirst { it.id == ruleId }
    if (index != -1) {
      rules[index] = ExclusionRule(ruleId, newType, newPattern)
      notifyListeners()
    }
  }

  fun getRule(ruleId: String): ExclusionRule? {
    return rules.firstOrNull { it.id == ruleId }
  }

  fun getRules(): List<ExclusionRule> {
    return rules.toList()
  }

  fun clearRules() {
    rules.clear()
    notifyListeners()
  }

  /**
   * Checks if the given URL should be excluded based on current rules.
   *
   * @param method HTTP method
   * @param url Full URL
   * @return true if the URL matches any exclusion rule
   */
  fun shouldExclude(method: String, url: String): Boolean {
    return rules.any { it.matches(method, url) }
  }

  fun addChangeListener(listener: Consumer<List<ExclusionRule>>) {
    listeners.add(listener)
  }

  fun removeChangeListener(listener: Consumer<List<ExclusionRule>>) {
    listeners.remove(listener)
  }

  private fun notifyListeners() {
    val currentRules = getRules()
    listeners.forEach { it.accept(currentRules) }
  }
}
