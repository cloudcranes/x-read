# Third-party notices

## Font character facts

`x_read/data/font_dnmr.json` derives only the character mapping data from
fysh1010/mcp-server-fanqie `src/fontDecrypt.ts`, fixed commit
`8bfe3ea4050da5f4689a4267f7e55958eb440f13`.
Source: https://github.com/fysh1010/mcp-server-fanqie/tree/8bfe3ea4050da5f4689a4267f7e55958eb440f13
The upstream file credits research at https://blog.csdn.net/m0_57265868/article/details/137511029 .
Only DNMrHsV173Pd4pgy font is supported. No JavaScript is executed.

MIT License

Copyright (c) 2026 fysh1010

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


## Android native content core

The independent Python HTTP client uses the documented wire contract of
Maicy0609/fanqie-lite-api, docs/API.md and crates/fqapi-core/src at commit
c128860ec99947eb397acb64804911c3f85f4631 (GPL-3.0). The vendored core ships in the Android APK (`android/native-core`); the Python server does not embed it.
