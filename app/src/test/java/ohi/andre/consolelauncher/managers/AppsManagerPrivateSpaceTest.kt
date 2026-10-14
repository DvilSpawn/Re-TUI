package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class AppsManagerPrivateSpaceTest {
    @Test
    fun profileTypesDoNotTreatPrivateProfilesAsWorkProfiles() {
        assertEquals(
            AppsManager.LaunchInfo.ProfileType.PRIVATE,
            AppsManager.profileTypeForUserType(
                "android.os.usertype.profile.PRIVATE",
                current = false,
                canAccessPrivateProfiles = true
            )
        )
        assertEquals(
            AppsManager.LaunchInfo.ProfileType.OTHER,
            AppsManager.profileTypeForUserType(
                "android.os.usertype.profile.PRIVATE",
                current = false,
                canAccessPrivateProfiles = false
            )
        )
        assertEquals(
            AppsManager.LaunchInfo.ProfileType.MANAGED,
            AppsManager.profileTypeForUserType(
                "android.os.usertype.profile.MANAGED",
                current = false,
                canAccessPrivateProfiles = true
            )
        )
    }

    @Test
    fun privateProfileVisibilityIsLockedToThePrivateSurface() {
        assertEquals(true, AppsManager.isNormalSurfaceProfile(AppsManager.LaunchInfo.ProfileType.CURRENT))
        assertEquals(true, AppsManager.isNormalSurfaceProfile(AppsManager.LaunchInfo.ProfileType.MANAGED))
        assertEquals(false, AppsManager.isNormalSurfaceProfile(AppsManager.LaunchInfo.ProfileType.PRIVATE))
        assertEquals(true, AppsManager.isPrivateSurfaceProfile(AppsManager.LaunchInfo.ProfileType.PRIVATE, unlocked = true))
        assertEquals(false, AppsManager.isPrivateSurfaceProfile(AppsManager.LaunchInfo.ProfileType.PRIVATE, unlocked = false))
        assertEquals(false, AppsManager.isPrivateSurfaceProfile(AppsManager.LaunchInfo.ProfileType.MANAGED, unlocked = true))
    }
}
