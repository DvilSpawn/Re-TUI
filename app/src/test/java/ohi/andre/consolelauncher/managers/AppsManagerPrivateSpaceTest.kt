package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Test
import ohi.andre.consolelauncher.R

class AppsManagerPrivateSpaceTest {
    @Test
    fun privateAppsRequireBothQuietModeOffAndAnActuallyUnlockedProfile() {
        assertEquals(false, AppsManager.privateProfileReady(quietMode = false, userUnlocked = false))
        assertEquals(false, AppsManager.privateProfileReady(quietMode = true, userUnlocked = false))
        assertEquals(false, AppsManager.privateProfileReady(quietMode = true, userUnlocked = true))
        assertEquals(true, AppsManager.privateProfileReady(quietMode = false, userUnlocked = true))
    }

    @Test
    fun unlockNeedingCredentialsIsPendingAuthenticationRatherThanFailure() {
        assertEquals(
            R.string.output_private_space_auth_required,
            AppsManager.privateSpaceRequestMessage(quietMode = false, Result.success(false))
        )
    }

    @Test
    fun acceptedRequestsDoNotClaimTheTransitionIsFinished() {
        assertEquals(
            R.string.output_private_space_unlock_requested,
            AppsManager.privateSpaceRequestMessage(quietMode = false, Result.success(true))
        )
        assertEquals(
            R.string.output_private_space_locked_now,
            AppsManager.privateSpaceRequestMessage(quietMode = true, Result.success(true))
        )
    }

    @Test
    fun securityRejectionExplainsTheForegroundDefaultLauncherRequirement() {
        for (quietMode in listOf(false, true)) {
            assertEquals(
                R.string.output_private_space_foreground_required,
                AppsManager.privateSpaceRequestMessage(quietMode, Result.failure(SecurityException()))
            )
        }
    }

    @Test
    fun platformExceptionsAndRejectedLockStillReportFailure() {
        for (quietMode in listOf(false, true)) {
            assertEquals(
                R.string.output_private_space_action_failed,
                AppsManager.privateSpaceRequestMessage(quietMode, Result.failure(IllegalArgumentException()))
            )
        }
        assertEquals(
            R.string.output_private_space_action_failed,
            AppsManager.privateSpaceRequestMessage(quietMode = true, Result.success(false))
        )
    }

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
