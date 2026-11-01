package dev.reflux.android.playback

import android.media.MediaCodecInfo.CodecProfileLevel
import dev.reflux.core.playback.VideoProfile

/**
 * The platform's profile and level constants in Reflux's terms, for what a decoder declares and what a file says
 * alike. Levels become the bitstream's own numbers, which is what servers report: HEVC 4.0 is 120, H.264 4.0 is 40,
 * VP9 4.0 is 40, and AV1 4.0 is 8. A constant not listed here is unknown, and stays so.
 */
@Suppress("InlinedApi") // Constants: compiled in, and harmless to compare with on an older system.
internal object AndroidProfiles {
    const val HEVC = "video/hevc"
    const val AVC = "video/avc"
    const val VP9 = "video/x-vnd.on2.vp9"
    const val AV1 = "video/av01"
    const val DOLBY_VISION = "video/dolby-vision"

    private val avcProfiles = mapOf(
        CodecProfileLevel.AVCProfileBaseline to VideoProfile.BASELINE, CodecProfileLevel.AVCProfileConstrainedBaseline to VideoProfile.BASELINE,
        CodecProfileLevel.AVCProfileMain to VideoProfile.MAIN, CodecProfileLevel.AVCProfileHigh to VideoProfile.HIGH,
        CodecProfileLevel.AVCProfileConstrainedHigh to VideoProfile.HIGH, CodecProfileLevel.AVCProfileHigh10 to VideoProfile.HIGH_10,
        CodecProfileLevel.AVCProfileHigh422 to VideoProfile.HIGH_422, CodecProfileLevel.AVCProfileHigh444 to VideoProfile.HIGH_444,
    )
    private val hevcProfiles = mapOf(
        CodecProfileLevel.HEVCProfileMain to VideoProfile.MAIN, CodecProfileLevel.HEVCProfileMain10 to VideoProfile.MAIN_10,
        CodecProfileLevel.HEVCProfileMain10HDR10 to VideoProfile.MAIN_10, CodecProfileLevel.HEVCProfileMain10HDR10Plus to VideoProfile.MAIN_10,
    )
    private val vp9Profiles = mapOf(
        CodecProfileLevel.VP9Profile0 to VideoProfile.PROFILE_0, CodecProfileLevel.VP9Profile1 to VideoProfile.PROFILE_1,
        CodecProfileLevel.VP9Profile2 to VideoProfile.PROFILE_2, CodecProfileLevel.VP9Profile2HDR to VideoProfile.PROFILE_2,
        CodecProfileLevel.VP9Profile3 to VideoProfile.PROFILE_3, CodecProfileLevel.VP9Profile3HDR to VideoProfile.PROFILE_3,
    )
    private val av1Profiles = mapOf(
        CodecProfileLevel.AV1ProfileMain8 to VideoProfile.MAIN, CodecProfileLevel.AV1ProfileMain10 to VideoProfile.MAIN,
        CodecProfileLevel.AV1ProfileMain10HDR10 to VideoProfile.MAIN, CodecProfileLevel.AV1ProfileMain10HDR10Plus to VideoProfile.MAIN,
    )

    private val avcLevels = mapOf(
        CodecProfileLevel.AVCLevel1 to 10, CodecProfileLevel.AVCLevel1b to 10, CodecProfileLevel.AVCLevel11 to 11, CodecProfileLevel.AVCLevel12 to 12,
        CodecProfileLevel.AVCLevel13 to 13, CodecProfileLevel.AVCLevel2 to 20, CodecProfileLevel.AVCLevel21 to 21, CodecProfileLevel.AVCLevel22 to 22,
        CodecProfileLevel.AVCLevel3 to 30, CodecProfileLevel.AVCLevel31 to 31, CodecProfileLevel.AVCLevel32 to 32, CodecProfileLevel.AVCLevel4 to 40,
        CodecProfileLevel.AVCLevel41 to 41, CodecProfileLevel.AVCLevel42 to 42, CodecProfileLevel.AVCLevel5 to 50, CodecProfileLevel.AVCLevel51 to 51,
        CodecProfileLevel.AVCLevel52 to 52, CodecProfileLevel.AVCLevel6 to 60, CodecProfileLevel.AVCLevel61 to 61, CodecProfileLevel.AVCLevel62 to 62,
    )
    private val hevcLevels = mapOf(
        CodecProfileLevel.HEVCMainTierLevel1 to 30, CodecProfileLevel.HEVCHighTierLevel1 to 30,
        CodecProfileLevel.HEVCMainTierLevel2 to 60, CodecProfileLevel.HEVCHighTierLevel2 to 60,
        CodecProfileLevel.HEVCMainTierLevel21 to 63, CodecProfileLevel.HEVCHighTierLevel21 to 63,
        CodecProfileLevel.HEVCMainTierLevel3 to 90, CodecProfileLevel.HEVCHighTierLevel3 to 90,
        CodecProfileLevel.HEVCMainTierLevel31 to 93, CodecProfileLevel.HEVCHighTierLevel31 to 93,
        CodecProfileLevel.HEVCMainTierLevel4 to 120, CodecProfileLevel.HEVCHighTierLevel4 to 120,
        CodecProfileLevel.HEVCMainTierLevel41 to 123, CodecProfileLevel.HEVCHighTierLevel41 to 123,
        CodecProfileLevel.HEVCMainTierLevel5 to 150, CodecProfileLevel.HEVCHighTierLevel5 to 150,
        CodecProfileLevel.HEVCMainTierLevel51 to 153, CodecProfileLevel.HEVCHighTierLevel51 to 153,
        CodecProfileLevel.HEVCMainTierLevel52 to 156, CodecProfileLevel.HEVCHighTierLevel52 to 156,
        CodecProfileLevel.HEVCMainTierLevel6 to 180, CodecProfileLevel.HEVCHighTierLevel6 to 180,
        CodecProfileLevel.HEVCMainTierLevel61 to 183, CodecProfileLevel.HEVCHighTierLevel61 to 183,
        CodecProfileLevel.HEVCMainTierLevel62 to 186, CodecProfileLevel.HEVCHighTierLevel62 to 186,
    )
    private val vp9Levels = mapOf(
        CodecProfileLevel.VP9Level1 to 10, CodecProfileLevel.VP9Level11 to 11, CodecProfileLevel.VP9Level2 to 20, CodecProfileLevel.VP9Level21 to 21,
        CodecProfileLevel.VP9Level3 to 30, CodecProfileLevel.VP9Level31 to 31, CodecProfileLevel.VP9Level4 to 40, CodecProfileLevel.VP9Level41 to 41,
        CodecProfileLevel.VP9Level5 to 50, CodecProfileLevel.VP9Level51 to 51, CodecProfileLevel.VP9Level52 to 52, CodecProfileLevel.VP9Level6 to 60,
        CodecProfileLevel.VP9Level61 to 61, CodecProfileLevel.VP9Level62 to 62,
    )
    private val av1Levels = mapOf(
        CodecProfileLevel.AV1Level2 to 0, CodecProfileLevel.AV1Level21 to 1, CodecProfileLevel.AV1Level22 to 2, CodecProfileLevel.AV1Level23 to 3,
        CodecProfileLevel.AV1Level3 to 4, CodecProfileLevel.AV1Level31 to 5, CodecProfileLevel.AV1Level32 to 6, CodecProfileLevel.AV1Level33 to 7,
        CodecProfileLevel.AV1Level4 to 8, CodecProfileLevel.AV1Level41 to 9, CodecProfileLevel.AV1Level42 to 10, CodecProfileLevel.AV1Level43 to 11,
        CodecProfileLevel.AV1Level5 to 12, CodecProfileLevel.AV1Level51 to 13, CodecProfileLevel.AV1Level52 to 14, CodecProfileLevel.AV1Level53 to 15,
        CodecProfileLevel.AV1Level6 to 16, CodecProfileLevel.AV1Level61 to 17, CodecProfileLevel.AV1Level62 to 18, CodecProfileLevel.AV1Level63 to 19,
        CodecProfileLevel.AV1Level7 to 20, CodecProfileLevel.AV1Level71 to 21, CodecProfileLevel.AV1Level72 to 22, CodecProfileLevel.AV1Level73 to 23,
    )

    /** The profile numbers of Dolby Vision, which is how files and servers name them. */
    private val dolbyVisionProfiles = mapOf(
        CodecProfileLevel.DolbyVisionProfileDvavPer to 0, CodecProfileLevel.DolbyVisionProfileDvavPen to 1,
        CodecProfileLevel.DolbyVisionProfileDvheDer to 2, CodecProfileLevel.DolbyVisionProfileDvheDen to 3,
        CodecProfileLevel.DolbyVisionProfileDvheDtr to 4, CodecProfileLevel.DolbyVisionProfileDvheStn to 5,
        CodecProfileLevel.DolbyVisionProfileDvheDth to 6, CodecProfileLevel.DolbyVisionProfileDvheDtb to 7,
        CodecProfileLevel.DolbyVisionProfileDvheSt to 8, CodecProfileLevel.DolbyVisionProfileDvavSe to 9,
        CodecProfileLevel.DolbyVisionProfileDvav110 to 10,
    )

    fun profile(mimeType: String, constant: Int): VideoProfile? = when (mimeType.lowercase()) {
        AVC -> avcProfiles[constant]
        HEVC -> hevcProfiles[constant]
        VP9 -> vp9Profiles[constant]
        AV1 -> av1Profiles[constant]
        else -> null
    }

    fun level(mimeType: String, constant: Int): Int? = when (mimeType.lowercase()) {
        AVC -> avcLevels[constant]
        HEVC -> hevcLevels[constant]
        VP9 -> vp9Levels[constant]
        AV1 -> av1Levels[constant]
        else -> null
    }

    fun dolbyVision(constant: Int): Int? = dolbyVisionProfiles[constant]

    /** Whether [constant] is a profile of 10 bits or more. On AV1 that is the constant, and not the profile. */
    fun tenBit(mimeType: String, constant: Int): Boolean = when (mimeType.lowercase()) {
        AVC -> constant == CodecProfileLevel.AVCProfileHigh10
        HEVC -> constant == CodecProfileLevel.HEVCProfileMain10 || constant == CodecProfileLevel.HEVCProfileMain10HDR10 ||
            constant == CodecProfileLevel.HEVCProfileMain10HDR10Plus
        VP9 -> constant == CodecProfileLevel.VP9Profile2 || constant == CodecProfileLevel.VP9Profile2HDR ||
            constant == CodecProfileLevel.VP9Profile3 || constant == CodecProfileLevel.VP9Profile3HDR
        AV1 -> constant == CodecProfileLevel.AV1ProfileMain10 || constant == CodecProfileLevel.AV1ProfileMain10HDR10 ||
            constant == CodecProfileLevel.AV1ProfileMain10HDR10Plus
        else -> false
    }

    /** Each declared profile with the highest level declared for it; null when no profile could be read. */
    fun declared(mimeType: String, profileLevels: List<Pair<Int, Int>>): Map<VideoProfile, Int?>? {
        val named = profileLevels.mapNotNull { (profile, level) -> profile(mimeType, profile)?.let { it to level(mimeType, level) } }
        if (named.isEmpty()) return null
        return named.groupBy({ it.first }, { it.second }).mapValues { (_, levels) -> if (null in levels) null else levels.filterNotNull().max() }
    }
}
