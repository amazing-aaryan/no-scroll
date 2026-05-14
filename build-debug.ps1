$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --stop 2>$null
Remove-Item -Recurse -Force "app\build\outputs" -ErrorAction SilentlyContinue
.\gradlew.bat assembleDebug
if ($LASTEXITCODE -eq 0) {
    .\gradlew.bat installDebug
}
