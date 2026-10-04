# Stop the services started by run.ps1
Set-Location $PSScriptRoot
if (Test-Path logs\pids.txt) {
    foreach ($id in Get-Content logs\pids.txt) {
        try { taskkill /PID $id /T /F; Write-Host "stopped pid $id" } catch {}
    }
    Remove-Item logs\pids.txt
}
