@echo off
setlocal
set ROOT=%~dp0..
if not exist "%ROOT%\third_party" mkdir "%ROOT%\third_party"

set LLAMA_COMMIT=5b59b83f4e2101ea173d4f853a0522d9971f48c6
set WHISPER_COMMIT=5670d5c0bbcb148feabef84400a07cfca9aa3b30

if not exist "%ROOT%\third_party\llama.cpp\.git" git clone https://github.com/ggml-org/llama.cpp.git "%ROOT%\third_party\llama.cpp"
git -C "%ROOT%\third_party\llama.cpp" fetch --depth 1 origin %LLAMA_COMMIT%
git -C "%ROOT%\third_party\llama.cpp" checkout --detach %LLAMA_COMMIT%

if not exist "%ROOT%\third_party\whisper.cpp\.git" git clone https://github.com/ggml-org/whisper.cpp.git "%ROOT%\third_party\whisper.cpp"
git -C "%ROOT%\third_party\whisper.cpp" fetch --depth 1 origin %WHISPER_COMMIT%
git -C "%ROOT%\third_party\whisper.cpp" checkout --detach %WHISPER_COMMIT%

del /q "%ROOT%\third_party\whisper.cpp\examples\whisper.android\lib\build.gradle" 2>nul
copy /y "%ROOT%\scripts\whisper-lib.build.gradle.kts" "%ROOT%\third_party\whisper.cpp\examples\whisper.android\lib\build.gradle.kts"
echo Native dependencies ready.
