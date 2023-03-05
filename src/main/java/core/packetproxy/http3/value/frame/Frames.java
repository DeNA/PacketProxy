/*
 * Copyright 2023 DeNA Co., Ltd.
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

package packetproxy.http3.value.frame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Frames {

    static public Frames emptyList() {
        return new Frames();
    }
    static public Frames of(Frame frame) {
        return new Frames(List.of(frame));
    }
    static public Frames of(Frame frame1, Frame frame2) {
        return new Frames(List.of(frame1, frame2));
    }
    static public Frames of(Frame frame1, Frame frame2, Frame frame3) {
        return new Frames(List.of(frame1, frame2, frame3));
    }

    private final ArrayList<Frame> frames = new ArrayList<>();

    private Frames() {
    }

    private Frames(List<Frame> frames) {
        this.frames.addAll(frames);
    }

    public void clear() {
        this.frames.clear();
    }

    public boolean add(Frame frame) {
        return this.frames.add(frame);
    }

    public boolean addAll(Frames frames) {
        return this.frames.addAll(frames.frames);
    }

    public Frame get(int index) {
        return this.frames.get(index);
    }

    public int size() {
        return this.frames.size();
    }

    public boolean isEmpty() {
        return this.frames.isEmpty();
    }

    public void forEach(Consumer<Frame> action) {
        this.frames.forEach(action);
    }

    public boolean anyMatch(Predicate<Frame> pred) {
        return this.frames.stream().anyMatch(pred);
    }

    public List<Frame> toList() {
        return (ArrayList<Frame>) this.frames.clone();
    }

}
