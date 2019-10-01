/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.common;

import java.util.Arrays;

/**
 * ボイヤー・ムーアアルゴリズムの簡易版
 * 検索するパターンの長さをm, テキストの長さを n として、時間計算量の平均は O(n) となる。
 * Good Suffix Ruleを実装していないので最悪のケースでは O(nm) となる。(Good Suffix Ruleを実装している場合は O(n + m) )
 * ただし、よほど変なテキストを食わせない限りは最悪のケースには陥らず、概ね n に比例する時間で計算が完了する。
 *
 * 4KBのランダムな文字列から12バイト程度の部分文字列を検索する場合、Bruteforceで検索するより平均して2倍速かった。 (1500ns -> 800ns)
 * //TODO Good Suffix Ruleを実装する場合は、JMHなどでベンチマークをとって実際に速くなることを確認する。
 *
 * @author 星本
 * @version 0.1.0
 */
public class BoyerMoore {

    private final byte[] pattern;
    private final int[] charTable;

    /**
     * ボイヤー・ムーアの文字列検索アルゴリズム (BM法) でパターンを検索するオブジェクトを生成する
     * インスタンス化するたびに「文字が不一致だったときにスキップする距離を計算するのに使う表」を構築するため、
     * 同じパターンで検索する場合はインスタンスを使い回した方が良い。
     * 
     * @param pattern 検索したいバイト列
     */
    public BoyerMoore(byte[] pattern) {
        this.pattern = pattern;
        charTable = new int[256 * (pattern.length + 1)];

        int nsofar = 0;
        int[] sofar = new int[256];
        int[] occurred = new int[8];
        Arrays.fill(charTable, -1);
        Arrays.fill(occurred, 0);
        for (int i = 0; i < pattern.length; i++) {
            int c1 = 0x00ff & ((int) pattern[i]);
            int idx = c1 + 256 * (i + 1);
            charTable[idx] = i;

            // Arrays.fillとかで毎回fillするととても遅いので、必要な要素だけに絞ってテーブルを更新する
            if (i > 0) {
                for (int j = 0; j < nsofar; j++) {
                    int c2 = sofar[j];
                    if (c1 != c2) {
                        charTable[c2 + (256 * (i + 1))] = charTable[c2 + (256 * i)];
                    }
                }
            }

            // その「必要な要素」をトラックするためのリストを更新する
            // これまでに出現したアルファベットの検索は効率のためBitsetを使う
            //TODO もう少し筋の良いやり方がある気がする
            if (!exists(occurred, c1)) {
                sofar[nsofar++] = c1;
                setBit(occurred, c1);
            }
        }
    }

    private boolean exists(int[] bitset, int c) {
        int q = c >> 5;
        int r = c & 0x1f;
        return (bitset[q] & (1 << r)) != 0x00;
    }

    private void setBit(int[] bitset, int c) {
        int q = c >> 5;
        int r = c & 0x1f;
        bitset[q] = bitset[q] | (1 << r);
    }

    /**
     * 与えられたバイト列の中から、インスタンス化時で指定したパターン (バイト列) を検索する
     * はじめに見つかった部分文字列の開始位置のインデックスを返す。
     *
     * @param text 検索対象のテキスト
     * @return はじめにみつかったパターンに一致する部分文字列の開始位置
     */
    public int searchIn(byte[] text) {
        return searchIn(text, 0);
    }

    /**
     * 与えられたバイト列の中から、インスタンス化時で指定したパターン (バイト列) を検索する
     * オフセットから、はじめに見つかった部分文字列の開始位置のインデックスまでの距離を返す。
     *
     * @param text 検索対象のテキスト
     * @return はじめにみつかったパターンに一致する部分文字列の開始位置
     */
    public int searchIn(byte[] text, int offset) {
        return searchIn(text, offset, text != null ? text.length : null);
    }

    // プリントデバッグが楽になるやつ
    public String getReadableTable(char... chars) {
        StringBuilder tbl = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            sb.append(chars[i]).append(",");
        }
        String columnNames = sb.toString();
        columnNames = columnNames.substring(0, columnNames.length() - 1);
        tbl.append(columnNames).append("\n");

        for (int i = 0; i < charTable.length; i += 256) {
            sb = new StringBuilder();
            for (int j = 0; j < chars.length; j++) {
                int idx = (((int) chars[j]) & 0x00ff) + i;
                sb.append(charTable[idx]);
                if (j != chars.length - 1) {
                    sb.append(",");
                }
            }
            tbl.append(sb.toString()).append("\n");
        }
        return tbl.toString();
    }
    
    /**
     * 与えられたバイト列のoffsetからendposまでの部分列の中から、インスタンス化時で指定したパターン (バイト列) を検索する
     * オフセットから、はじめに見つかった部分文字列の開始位置のインデックスまでの距離を返す。
     *
     * @param text 検索対象のテキスト
     * @return はじめにみつかったパターンに一致する部分文字列の開始位置
     */
    public int searchIn(byte[] text, int offset, int endpos) {
        if (offset < 0 || offset > text.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (pattern.length > 0 &&
            endpos >= offset &&
            pattern != null &&
            pattern.length <= text.length) {
            int i = offset + pattern.length - 1;
            while (i < Math.min(text.length, endpos)) {
                int j = pattern.length - 1;
                while (text[i] == pattern[j]) {
                    if (j == 0) {
                        return i - offset;
                    }
                    j--;
                    i--;
                }
                int idx = 0x00ff & ((int) text[i]);
                idx += 256 * j;
                int shift = pattern.length - 1 - charTable[idx];
                i += shift;
            }
        }
        return -1;
    }
}
