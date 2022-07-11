package com.contusfly.call.groupcall.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.contus.call.CallConstants
import com.contus.call.utils.GroupCallUtils
import com.contus.webrtc.ProxyVideoSink
import com.contus.webrtc.api.CallManager
import com.contusfly.*
import com.contusfly.call.groupcall.isUserVideoMuted
import com.contusfly.utils.Constants
import com.contusfly.views.CircularImageView
import com.contusfly.views.SetDrawable
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.utils.ChatUtils
import com.contusflysdk.utils.Utils
import java.util.ArrayList

object CallUtils {

    /**
     * flag indicates whether group call grid view is showing or not
     */
    private var isGridViewEnabled = false

    private var isVideoViewInitialized = false

    /**
     * flag indicates whether call grid/list view updates currently happening
     */
    private var isViewUpdatesCompleted = true

    /**
     * flag indicates whether list view is showing above call options
     */
    private var isListViewAnimated = false

    /**
     * Contains the user jid which need to be showed in pinned view
     */
    private var pinnedUserJid = Constants.EMPTY_STRING

    /**
     * flag indicates whether user tile pinned
     */
    private var isUserTilePinned = false

    /**
     * This indicates whether the back camera capturing or not
     */
    private var isBackCameraCapturing = false

    /**
     * flag indicates which user audio level reaches peak
     */
    private var peakSpeakingUser = SpeakingUser(Constants.EMPTY_STRING, 0)

    private val speakingLevelMap by lazy { HashMap<String, Int>() }

    fun setVideoViewInitialization(enabled: Boolean) {
        isVideoViewInitialized = enabled
    }

    fun getIsVideoViewInitialized() : Boolean {
        return isVideoViewInitialized
    }

    fun setIsGridViewEnabled(enabled: Boolean) {
        isGridViewEnabled = enabled
    }

    fun getIsGridViewEnabled() : Boolean {
        return isGridViewEnabled
    }

    fun setIsViewUpdatesCompleted(boolean: Boolean) {
        isViewUpdatesCompleted = boolean
    }

    fun getIsViewUpdatesCompleted() : Boolean {
        return isViewUpdatesCompleted
    }

    fun setIsListViewAnimated(animated: Boolean) {
        isListViewAnimated = animated
    }

    fun getIsListViewAnimated() : Boolean {
        return isListViewAnimated
    }

    fun setPinnedUserJid(userJid: String) {
        pinnedUserJid = userJid
    }

    fun getPinnedUserJid() : String {
        return pinnedUserJid
    }

    fun setIsUserTilePinned(pinned: Boolean) {
        isUserTilePinned = pinned
    }

    fun getIsUserTilePinned() : Boolean {
        return isUserTilePinned
    }

    fun setIsBackCameraCapturing(isBackCamera: Boolean) {
        isBackCameraCapturing = isBackCamera
    }

    fun getIsBackCameraCapturing() : Boolean {
        return isBackCameraCapturing
    }

    private fun setPeakSpeakingUser(userJid: String, audioLevel: Int) {
        peakSpeakingUser = SpeakingUser(userJid, audioLevel)
    }

    private fun getPeakSpeakingUser() : SpeakingUser {
        return peakSpeakingUser
    }

    fun isSpeakingUserCanBeShownOnTop(userJid: String, audioLevel: Int): Boolean {
        return userJid != GroupCallUtils.getLocalUserJid() // Local User view no need to move to top
                && !getIsUserTilePinned()  // If any user is pinned then no need to move to top
                && !GroupCallUtils.isOneToOneCall() // In 1-1 call no need to move speaking user to top
                && isSpeakingLevelsReceivedForSameUser(userJid, audioLevel)
                && !getIsGridViewEnabled() // In Grid view no need to move speaking user to top
    }

    /*
    * This function will decide whether speaking level received continuously for same user
    */
    private fun isSpeakingLevelsReceivedForSameUser(userJid: String, audioLevel: Int): Boolean {
        return if (getPeakSpeakingUser().userJid == userJid) {
            getPeakSpeakingUser().audioLevel = audioLevel
            if (getPeakSpeakingUser().count >= 2 && userJid != getPinnedUserJid()) {
                getPeakSpeakingUser().count = 0
                true
            } else {
                getPeakSpeakingUser().count++
                false
            }
        } else {
            if (getPeakSpeakingUser().audioLevel <= audioLevel)
                setPeakSpeakingUser(userJid, audioLevel)
            false
        }
    }

    fun onUserSpeaking(userJid: String, audioLevel:Int){
        speakingLevelMap[userJid] = audioLevel
    }

    fun onUserStoppedSpeaking(userJid: String) {
        speakingLevelMap[userJid] = 0
    }

    fun getUserSpeakingLevel(userJid: String): Int {
        return speakingLevelMap[userJid] ?: 0
    }

    fun clearSpeakingLevels(){
        speakingLevelMap.clear()
        setPeakSpeakingUser(Constants.EMPTY_STRING, 0)
    }

    fun clearPeakSpeakingUser(userJid: String) {
        if (getPeakSpeakingUser().userJid == userJid)
            setPeakSpeakingUser(userJid, 0)
    }

    fun setGroupMemberProfile(
        context: Context,
        callUsers: ArrayList<String>,
        imageCallMember1: CircularImageView,
        imageCallMember2: CircularImageView,
        imageCallMember3: CircularImageView,
        imageCallMember4: CircularImageView
    ): StringBuilder {
        makeViewsGone(imageCallMember2, imageCallMember3, imageCallMember4)
        var membersName = StringBuilder("")
        var isMaxMemberNameNotReached = true
        for (i in callUsers.indices) {
            val pair = getNameAndProfileDetails(callUsers[i])
            if (i == 0) {
                val actualMemberName = getActualMemberName(StringBuilder(pair.first))
                membersName = actualMemberName.first
                isMaxMemberNameNotReached = actualMemberName.second
                loadUserProfilePic(context, imageCallMember1, pair)
            } else if (isMaxMemberNameNotReached && i == 1) {
                membersName.append(", ").append(pair.first)
                val actualMemberName = getActualMemberName(membersName)
                membersName = actualMemberName.first
                isMaxMemberNameNotReached = actualMemberName.second
                imageCallMember2.show()
                loadUserProfilePic(context, imageCallMember2, pair)
            } else if (isMaxMemberNameNotReached && i == 2) {
                membersName.append(", ").append(pair.first)
                val actualMemberName = getActualMemberName(membersName)
                membersName = actualMemberName.first
                imageCallMember3.show()
                loadUserProfilePic(context, imageCallMember3, pair)
            } else {
                membersName.append(" (+").append(callUsers.size - i).append(")")
                imageCallMember4.show()
                val text = "+${callUsers.size - i}"
                val setDrawable = SetDrawable(context)
                imageCallMember4.setImageDrawable(setDrawable.setDrawableForCustomName(text))
                break
            }
        }
        return membersName
    }

    private fun loadUserProfilePic(
        context: Context,
        callMember: CircularImageView,
        pair: Pair<String, ProfileDetails?>
    ) {
        if (pair.second != null) callMember.loadUserProfileImage(context, pair.second!!)
        else callMember.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_profile))
    }

    private fun getNameAndProfileDetails(jid: String): Pair<String, ProfileDetails?> {
        val profileDetails = ContactManager.getProfileDetails(jid)
        val name = if (profileDetails != null) {
            com.contusfly.utils.Utils.returnEmptyStringIfNull(profileDetails.name)
        } else Utils.getFormattedPhoneNumber(ChatUtils.getUserFromJid(jid)) ?: Constants.EMPTY_STRING
        return Pair(name, profileDetails)
    }


   private fun getActualMemberName(stringBuilder: java.lang.StringBuilder): Pair<StringBuilder, Boolean> {
        return if (stringBuilder.length > CallConstants.MAX_NAME_LENGTH)
            Pair(
                StringBuilder(stringBuilder.substring(0, CallConstants.MAX_NAME_LENGTH)).append("..."),
                false
            )
        else
            Pair(stringBuilder, true)
    }

    fun getGroupMembersName(callUsers: ArrayList<String>): String {
        var membersName = StringBuilder(if (callUsers.size <= 1) "You and " else "You, ")
        var isMaxMemberNameNotReached = true
        for (i in callUsers.indices) {
            val pair = getNameAndProfileDetails(callUsers[i])
            if (i == 0) {
                membersName.append(pair.first)
                val actualMemberName = getActualMemberName(membersName)
                membersName = actualMemberName.first
                isMaxMemberNameNotReached = actualMemberName.second
            } else if (isMaxMemberNameNotReached && i == 1) {
                membersName.append(", ").append(pair.first)
                val actualMemberName = getActualMemberName(membersName)
                membersName = actualMemberName.first
                isMaxMemberNameNotReached = actualMemberName.second
            } else if (isMaxMemberNameNotReached && i == 2) {
                membersName.append(", ").append(pair.first)
                val actualMemberName = getActualMemberName(membersName)
                membersName = actualMemberName.first
            } else {
                membersName.append(" (+").append(callUsers.size - i).append(")")
                break
            }
        }
        return membersName.toString()
    }

    fun getPinnedVideoSink(): ProxyVideoSink? {
        return if (getPinnedUserJid() == GroupCallUtils.getLocalUserJid())
            CallManager.getLocalProxyVideoSink()
        else
            CallManager.getRemoteProxyVideoSink(getPinnedUserJid())
    }

    fun getVideoSinkForUser(userJid: String): ProxyVideoSink? {
        return if (userJid == GroupCallUtils.getLocalUserJid())
            CallManager.getLocalProxyVideoSink()
        else
            CallManager.getRemoteProxyVideoSink(userJid)
    }

    fun isLocalUserPinned() : Boolean {
        return getPinnedUserJid() == GroupCallUtils.getLocalUserJid()
    }

    fun getPinnedUserVideoMuted(): Boolean {
        return GroupCallUtils.isUserVideoMuted(getPinnedUserJid())
    }

    fun resetValues() {
        setIsGridViewEnabled(false)
        setIsBackCameraCapturing(false)
        setIsViewUpdatesCompleted(true)
        setIsListViewAnimated(false)
        setPinnedUserJid(Constants.EMPTY_STRING)
        setIsUserTilePinned(false)
        setPeakSpeakingUser(Constants.EMPTY_STRING, 0)
    }
}

data class SpeakingUser(
    val userJid: String,
    var audioLevel: Int,
    var count:Int
) {
    constructor(userJid: String, audioLevel: Int) : this(userJid, audioLevel, 1)
}

data class UserMuteStatus(
    val isVideoMuted: Boolean,
    val isAudioMuted: Boolean
)