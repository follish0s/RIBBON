# LOD 编译验证脚本（仅语法/API 验证，非正式构建）
# 用 corretto-22（英文路径，避免中文路径编码问题）+ raw.jar(Mojmap MC) + fml/bus + 依赖
$javac = "C:\Users\hemin\.jdks\corretto-22.0.2\bin\javac.exe"

$raw       = "D:\ribbon-21.1-neoforge\.gradle\configuration\neoForm\neoFormJoined1.21.1-20240808.144430\raw.jar"
$loader    = "C:\Users\hemin\.gradle\caches\modules-2\files-2.1\net.neoforged.fancymodloader\loader\4.0.45\4a0968c2ec6234d58e0a9ed97acafc08cc8f33b5\loader-4.0.45.jar"
$bus       = "C:\Users\hemin\.gradle\caches\modules-2\files-2.1\net.neoforged\bus\8.0.2\163c9ef7c4bcca6d850c34e95261b606af91fe06\bus-8.0.2.jar"
$slf4j     = "C:\Users\hemin\.gradle\caches\minecraft\libraries\org\slf4j\slf4j-api\2.0.9\slf4j-api-2.0.9.jar"
$joml      = "C:\Users\hemin\.gradle\caches\minecraft\libraries\org\joml\joml\1.10.5\joml-1.10.5.jar"
$fastutil  = "C:\Users\hemin\.gradle\caches\minecraft\libraries\it\unimi\dsi\fastutil\8.5.12\fastutil-8.5.12.jar"
$guava     = "C:\Users\hemin\.gradle\caches\minecraft\libraries\com\google\guava\guava\32.1.2-jre\guava-32.1.2-jre.jar"
$datafixer = "C:\Users\hemin\.gradle\caches\minecraft\libraries\com\mojang\datafixerupper\8.0.16\datafixerupper-8.0.16.jar"
$lwjgl     = "C:\Users\hemin\.gradle\caches\minecraft\libraries\org\lwjgl\lwjgl\3.3.3\lwjgl-3.3.3.jar"
$lwjglgl   = "C:\Users\hemin\.gradle\caches\minecraft\libraries\org\lwjgl\lwjgl-opengl\3.3.3\lwjgl-opengl-3.3.3.jar"

$cp = @($raw,$loader,$bus,$slf4j,$joml,$fastutil,$guava,$datafixer,$lwjgl,$lwjglgl) -join ";"

$out = "D:\ribbon-21.1-neoforge\build\ribbon-diag-check"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$src = "D:\ribbon-21.1-neoforge\src\main\java"
$files = @(
    "$src\com\example\ribbon\Config.java",
    "$src\com\example\ribbon\IrisReflection.java",
    "$src\com\example\ribbon\lod\data\LODChunk.java",
    "$src\com\example\ribbon\lod\data\LODColorPalette.java",
    "$src\com\example\ribbon\lod\io\BlockStateColorExtractor.java",
    "$src\com\example\ribbon\lod\io\McaRegionReader.java",
    "$src\com\example\ribbon\lod\mesh\LODMesh.java",
    "$src\com\example\ribbon\lod\mesh\GreedyMesher.java",
    "$src\com\example\ribbon\lod\LODManager.java",
    "$src\com\example\ribbon\lod\render\LODShaderRegistry.java",
    "$src\com\example\ribbon\lod\render\LODVboUploader.java",
    "$src\com\example\ribbon\lod\render\LODRenderEventHandler.java"
)

Write-Output "=== Compiling LOD sources (syntax/API check) ==="
& $javac -nowarn -encoding UTF-8 -cp $cp -d $out $files 2>&1
Write-Output "javac exit code: $LASTEXITCODE"
