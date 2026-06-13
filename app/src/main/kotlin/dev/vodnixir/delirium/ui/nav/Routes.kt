package dev.vodnixir.delirium.ui.nav

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data object MainRoute

@Serializable
data object AddFriendRoute

@Serializable
data object ProfileRoute

@Serializable
data object SettingsRoute

@Serializable
data object WidgetSetupRoute

@Serializable
data object RecapRoute

@Serializable
data class FeedRoute(val connectionId: String, val friendName: String)

@Serializable
data class PhotoDetailRoute(val photoId: String, val connectionId: String)

@Serializable
data class CameraRoute(val connectionId: String)

@Serializable
data class DrawRoute(val connectionId: String, val backgroundUri: String? = null)
