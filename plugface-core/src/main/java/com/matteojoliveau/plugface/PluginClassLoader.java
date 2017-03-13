package com.matteojoliveau.plugface;

/*-
 * #%L
 * plugface-core
 * %%
 * Copyright (C) 2017 Matteo Joliveau
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 * #L%
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.SecurityPermission;
import java.util.Properties;
import java.util.jar.JarFile;

public class PluginClassLoader extends URLClassLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginClassLoader.class);
    private static final char UNIX_SEPARATOR = '/';
    private static final char WINDOWS_SEPARATOR = '\\';
    private File permissionsFile;

    public PluginClassLoader(URL jarFileUrl) {
        super(new URL[]{jarFileUrl});
    }

    public PluginClassLoader(URL[] jarFileUrls) {
        super(jarFileUrls);
    }

    @Override
    protected PermissionCollection getPermissions(CodeSource codesource) {
        Permissions permissions = new Permissions();
        if (permissionsFile != null) {
            String pluginFileName = retrieveFileNameFromCodeSource(codesource);

            final String[] properties = new String[]{
                    "permissions." + pluginFileName + ".files",
                    "permissions." + pluginFileName + ".network",
                    "permissions." + pluginFileName + ".policyManagement",
                    "permissions." + pluginFileName + ".runtime"
            };

            Properties prop = new Properties();
            String requiredPermissions;

            InputStream input = null;
            try {
                input = new FileInputStream(permissionsFile);
            } catch (FileNotFoundException e) {
                LOGGER.error("Permission file not found", e);
            }
            try {
                prop.load(input);
            } catch (IOException e) {
                LOGGER.error("Error reading properties", e);
            }

            if (prop.isEmpty()) {
                return permissions;
            }

            for (String key : properties) {
                if (prop.containsKey(key)) {
                    requiredPermissions = prop.getProperty(key);
                    String[] slices = requiredPermissions.split(", ");

                    if (key.equals(properties[0])) {
                        for (String s : slices) {
                            String[] params = s.split(" ");
                            permissions.add(new FilePermission(params[1], params[0]));
                        }
                    } else if (key.equals(properties[1])) {
                        for (String s : slices) {
                            String[] params = s.split(" ");
                            permissions.add(new NetPermission(params[0]));
                        }
                    } else if (key.equals(properties[2])) {
                        for (String s : slices) {
                            String[] params = s.split(" ");
                            permissions.add(new SecurityPermission(params[0]));
                        }
                    } else if (key.equals(properties[3])) {
                        for (String s : slices) {
                            String[] params = s.split(" ");
                            permissions.add(new RuntimePermission(params[0]));
                        }


                    }
                }
            }
        }
        return permissions;
    }

    private String retrieveFileNameFromCodeSource(CodeSource codeSource) {
        JarURLConnection connection = null;
        try {
            connection = (JarURLConnection) codeSource.getLocation().openConnection();

        } catch (IOException e) {
            LOGGER.error("Can't open a connection to the codesource", e);
        }

        if (connection == null) {
            throw new IllegalStateException("Can't open a connection to the codesource");
        }

        String file;
        try {
            file = getName(connection.getJarFile().getName());
        } catch (IOException e) {
            throw new IllegalStateException("Error reading JarFile: " + e.getMessage(), e);
        }
        if (file == null) {
            throw new IllegalStateException("Error reading JarFile");
        }
        return file.substring(0, file.length() - 4);
    }

    public File getPermissionsFile() {
        return permissionsFile;
    }

    public void setPermissionsFile(File permissionsFile) {
        this.permissionsFile = permissionsFile;
    }

    private static String getName(String filename) {
        if (filename == null) {
            return null;
        }
        int index = indexOfLastSeparator(filename);
        return filename.substring(index + 1);
    }

    private static int indexOfLastSeparator(String filename) {
        if (filename == null) {
            return -1;
        }
        int lastUnixPos = filename.lastIndexOf(UNIX_SEPARATOR);
        int lastWindowsPos = filename.lastIndexOf(WINDOWS_SEPARATOR);
        return Math.max(lastUnixPos, lastWindowsPos);
    }
}
