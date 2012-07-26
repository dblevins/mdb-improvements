/*
 * Copyright 2012 David Blevins
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.superconnectors.telnet.api;

import java.util.regex.Pattern;

public interface TelnetListener {
    public String doDate();

    public String doJoke();

    public String doList(Pattern pattern);

    public String doSet(String key, String value);

    public String doGet(String key);

    public int doAdd(int a, int b);
}
