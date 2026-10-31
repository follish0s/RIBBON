Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = "D:\ribbon-21.1-neoforge\.gradle\configuration\neoForm\neoFormJoined1.21.1-20240808.144430\raw.jar"
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)

$targets = @(
    "com/mojang/blaze3d/systems/RenderSystem`$AutoStorageIndexBuffer.class"
)
foreach ($t in $targets) {
    $entry = $zip.Entries | Where-Object { $_.FullName -eq $t }
    if ($null -eq $entry) { Write-Output "=== NOT FOUND: $t ==="; continue }
    $ms = New-Object System.IO.MemoryStream
    $s = $entry.Open(); $s.CopyTo($ms); $s.Close()
    $text = [System.Text.Encoding]::ASCII.GetString($ms.ToArray())
    Write-Output "=== $t ==="
    Write-Output (([regex]::Matches($text, '[a-zA-Z_][a-zA-Z0-9_$]{1,50}') | ForEach-Object { $_.Value } | Select-Object -Unique) -join " ")
}
$zip.Dispose()
