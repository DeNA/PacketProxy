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
package packetproxy.gui;

import javax.swing.*;

public class Splash{

    private JWindow mSplashScreen;

    public Splash(){
        createSplash();
    }

    private void runAsync(final Runnable runnable){
        if(SwingUtilities.isEventDispatchThread()){
            runnable.run();
        }else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private void createSplash(){
        ImageIcon img = new ImageIcon(getClass().getResource("/gui/splash.png"));
        JLabel splashLabel   = new JLabel(img);
        mSplashScreen  = new JWindow(new JFrame());
        mSplashScreen.getContentPane().add(splashLabel);
        mSplashScreen.pack();
        mSplashScreen.setLocationRelativeTo(null);
    }

    public void show(){
        runAsync(new Runnable() {
            @Override
            public void run() {
                mSplashScreen.setVisible(true);
            }
        });
    }

    public void close(){
        runAsync(new Runnable() {
            @Override
            public void run() {
                mSplashScreen.setVisible(false);
                mSplashScreen = null;
            }
        });
    }
}
