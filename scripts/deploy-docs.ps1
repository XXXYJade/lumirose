<#
.SYNOPSIS
  重建文档站并写入本地 gh-pages 分支的 v1.1 / v1 子树。

.DESCRIPTION
  - 用版本化 site_url 构建，避免 canonical 丢版本段（/v1.1/、/v1/）。
  - 只替换 gh-pages 中的 v1.1/ 与 v1/，保留 v1.0/、latest 符号链接、versions.json。
  - 不改动仓库里的 mkdocs.yml / mkdocs.v1.yml，临时配置在 finally 中清理。
  - 默认只更新本地 gh-pages 分支；加 -Push 才推送到 origin（force-with-lease）。

.PARAMETER Push
  推送 gh-pages 到 origin。

.EXAMPLE
  ./scripts/deploy-docs.ps1
  ./scripts/deploy-docs.ps1 -Push
#>
param([switch]$Push)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$mkdocs = Join-Path $root '.venv\Scripts\mkdocs.exe'
if (-not (Test-Path $mkdocs)) { throw "mkdocs not found: $mkdocs" }

$tmpV11 = Join-Path $root 'mkdocs.deploy.tmp.yml'
$tmpV1 = Join-Path $root 'mkdocs.v1.deploy.tmp.yml'
$idxFile = Join-Path $env:TEMP ('ghidx_' + [guid]::NewGuid().ToString('N'))

try {
    $siteUrlV11 = 'https://xxxyjade.github.io/lumirose/v1.1/'
    $siteUrlV1 = 'https://xxxyjade.github.io/lumirose/v1/'
    (Get-Content 'mkdocs.yml' -Raw) -replace '(?m)^site_url:.*$', "site_url: $siteUrlV11" |
        Set-Content -Path $tmpV11 -Encoding utf8 -NoNewline
    (Get-Content 'mkdocs.v1.yml' -Raw) -replace '(?m)^site_url:.*$', "site_url: $siteUrlV1" |
        Set-Content -Path $tmpV1 -Encoding utf8 -NoNewline

    Remove-Item 'site', 'site-v1' -Recurse -Force -ErrorAction SilentlyContinue
    & $mkdocs build -f $tmpV11 --strict
    if ($LASTEXITCODE -ne 0) { throw 'v1.1 build failed' }
    & $mkdocs build -f $tmpV1 -d site-v1 --strict
    if ($LASTEXITCODE -ne 0) { throw 'v1 build failed' }

    $env:GIT_INDEX_FILE = $idxFile
    git read-tree gh-pages
    git rm -r --cached -q --ignore-unmatch v1 v1.1

    $siteRoot = (Resolve-Path 'site').Path
    Get-ChildItem 'site' -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($siteRoot.Length + 1).Replace('\', '/')
        $sha = (git hash-object -w -- $_.FullName 2>$null).Trim()
        git update-index --add --cacheinfo ("100755,$sha,v1.1/$rel") | Out-Null
    }

    $v1Root = (Resolve-Path 'site-v1').Path
    Get-ChildItem 'site-v1' -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($v1Root.Length + 1).Replace('\', '/')
        $sha = (git hash-object -w -- $_.FullName 2>$null).Trim()
        git update-index --add --cacheinfo ("100755,$sha,v1/$rel") | Out-Null
    }

    $tree = (git write-tree).Trim()
    $parent = (git rev-parse gh-pages).Trim()
    $msg = 'deploy: rebuild v1.1/v1 docs'
    $commit = (git commit-tree $tree -p $parent -m $msg).Trim()
    git update-ref refs/heads/gh-pages $commit

    "v1.1 files: " + (Get-ChildItem 'site' -Recurse -File | Measure-Object).Count
    "v1 files:   " + (Get-ChildItem 'site-v1' -Recurse -File | Measure-Object).Count
    "gh-pages:   $parent -> $commit"

    if ($Push) {
        git push --force-with-lease="refs/heads/gh-pages:$parent" origin "${commit}:refs/heads/gh-pages"
    } else {
        'local only; re-run with -Push to publish'
    }
}
finally {
    Remove-Item $tmpV11, $tmpV1 -Force -ErrorAction SilentlyContinue
    Remove-Item $idxFile -Force -ErrorAction SilentlyContinue
    Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue
}
