/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.impl.javase;

import com.codename1.ui.Display;
import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.util.Resources;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author shannah
 */
public class CSSWatcher implements Runnable {
    private Thread watchThread;
    private Process childProcess;
    private boolean closing;
    
    public CSSWatcher() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

            @Override
            public void run() {
                if (childProcess != null && childProcess.isAlive()) {
                    try {
                        closing = true;
                        childProcess.destroyForcibly();
                    } catch (Throwable t){}
                }
            }
            
        }));
    }
    
    private void watch() throws IOException {
        File javaBin = new File(System.getProperty("java.home"), "bin/java");
        final File srcFile = new File("css", "theme.css");
        if (!srcFile.exists()) {
            System.out.println("No theme.css file found.  CSSWatcher canceled");
            return;
        } else {
            System.out.println("Found theme.css file.  Watching for changes...");
        }
        final File destFile = new File("src", "theme.css.res");
        File userHome = new File(System.getProperty("user.home"));
        File cn1Home = new File(userHome, ".codenameone");
        File designerJar = new File(cn1Home, "designer_1.jar");
        
        ProcessBuilder pb = new ProcessBuilder(
                javaBin.getAbsolutePath(),
                "-jar", "-Dcli=true", designerJar.getAbsolutePath(),
                "-css",
                srcFile.getAbsolutePath(),
                "-watch",
                "-Dprism.order=sw"
                
        );
        Process p = pb.start();
        if (childProcess != null) {
            try {
                childProcess.destroyForcibly();
            } catch (Throwable t){}
        }
        childProcess = p;
        String line;
       
        OutputStream stdin = p.getOutputStream();
        final InputStream stderr = p.getErrorStream();
        final BufferedReader errorReader = new BufferedReader(new InputStreamReader(stderr));
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        String l = errorReader.readLine();
                        if (l != null) {
                            System.err.println("CSS> "+l);
                        } else {
                            break;
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(CSSWatcher.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }).start();
        
        InputStream stdout = p.getInputStream();
        

        BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
        
        while (true) {
            try {
                String l = reader.readLine();
                if (l == null) {
                    if (!p.isAlive()) {
                        watchThread = null;
                        start();
                        break;
                    }
                }
                System.out.println("CSS> "+l);
                if ("::refresh::".equals(l)) {
                    Display.getInstance().callSerially(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                System.out.println("CSS File "+srcFile+" has been updated.  Reloading styles from "+destFile);
                                Resources res = Resources.open(new FileInputStream(destFile));
                                UIManager.getInstance().addThemeProps(res.getTheme(res.getThemeResourceNames()[0]));
                                Display.getInstance().getCurrent().refreshTheme();
                                Display.getInstance().getCurrent().revalidate();
                            } catch(Exception err) {
                                err.printStackTrace();
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                if (closing) {
                    return;
                }
                t.printStackTrace();
                
                if (!p.isAlive()) {
                    watchThread = null;
                    start();
                    break;
                }
            }
        }
        
        
    }

    @Override
    public void run() {
        try {
            watch();
        } catch (Throwable t) {
            System.err.println("CSS watching failed");
            t.printStackTrace();
            watchThread = null;
        }
    }
    
    public void start() {
        if (watchThread == null) {
            watchThread = new Thread(this);
            watchThread.start();
        }
    }

    
    
    
    
}
