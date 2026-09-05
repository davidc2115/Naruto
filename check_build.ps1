$runId = "33966646399"
$url = "https://api.github.com/repos/davidc2115/Naruto/actions/runs/$runId/artifacts"
$res = Invoke-RestMethod -Uri $url
foreach ($art in $res.artifacts) {
    Write-Host "Artifact: $($art.name) | Size: $($art.size_in_bytes) | Download URL: $($art.archive_download_url)"
}
