package dev.vodnixir.delirium.ui.nav

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object FriendsRoute

@Serializable
data object AddFriendRoute

@Serializable
data class FeedRoute(val connectionId: String, val friendName: String)

@Serializable
data class CameraRoute(val connectionId: String)

@Serializable
data class DrawRoute(val connectionId: String, val backgroundUri: String? = null)
