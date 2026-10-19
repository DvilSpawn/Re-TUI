package ohi.andre.consolelauncher.presentation

import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusPresentationTest {
    private fun appearance(vararg values:Pair<Ui,String>):HomeAppearance {
        val map=mutableMapOf<XMLPrefsSave,String?>()
        StatusPresentation.descriptors.forEach { map[it.visible]="false" }
        values.forEach { (key,value)->map[key]=value }
        return HomeAppearance(map)
    }

    @Test fun groupsVisibleLogicalMembersWithoutInventingRows() {
        val appearance=appearance(Ui.show_ram to "true",Ui.show_time to "true",Ui.show_device_name to "true",
            Ui.ram_index to "1",Ui.time_index to "1.2",Ui.device_index to "3")
        assertEquals(listOf(listOf(0,2),listOf(1)),StatusPresentation.rows(appearance))
    }

    @Test fun draftInsertionUsesTheSameResolverAsSaveAndLeavesSavedRowsIntact() {
        val saved=appearance(Ui.show_ram to "true",Ui.show_time to "true",Ui.show_device_name to "true",
            Ui.ram_index to "1",Ui.time_index to "2",Ui.device_index to "3")
        val edited=saved.overlay({ if(it==Ui.device_index) "1" else null },{ it==Ui.device_index })
        assertEquals(listOf(listOf(1),listOf(0),listOf(2)),StatusPresentation.rows(edited,saved))
        assertEquals(listOf(listOf(0),listOf(2),listOf(1)),StatusPresentation.rows(saved))
    }
}
