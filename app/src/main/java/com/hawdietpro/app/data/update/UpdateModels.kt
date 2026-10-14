package com.hawdietpro.app.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
)

/** Info rilis yang sudah diproses aplikasi. */
@Serializable
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String?,
    val pageUrl: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val apkPath: String) : UpdateState
    data class Error(val message: String, val release: ReleaseInfo? = null) : UpdateState
}
