#!/usr/bin/env python3
"""Host regression: real Activity method bodies + socket client, narrow Android/view spies.

No device required. View metrics/pre-draw are spies, not Android renderer acceptance.
Uses the Kotlin compiler already cached by Gradle; run after a normal Gradle build.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt'


def method(source, name):
    start = source.index('    private fun ' + name + '(')
    end = source.find('\n    private fun ', start + 1)
    return source[start:end if end >= 0 else source.rfind('\n}')].replace('private fun', 'fun', 1)


def block(source, start):
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def main():
    source = SOURCE.read_text()
    bodies = '\n'.join(method(source, name) for name in ('style', 'geometry', 'capture', 'mutation', 'scheduleGeometryCapture'))
    if 'private fun cancelGeometryCapture(' in source:
        bodies += '\n' + method(source, 'cancelGeometryCapture')
    for name in ('onPause', 'onResume'):
        bodies += '\n' + block(source, source.index('    override fun ' + name + '('))
    attach = ''
    if 'tiles.addOnAttachStateChangeListener' in source:
        attach = block(source, source.index('tiles.addOnAttachStateChangeListener')) + ')'
    bodies += '\n fun installAttachListener() { ' + attach + ' }'
    frame_start = source.index('override fun onFrame(raw: String)')
    frame_callback = block(source, source.index('main.post {', frame_start))
    bodies += '\n fun acceptFrame(tile:Int, client:TermuxWorkspaceSocketClient, raw:String) { ' + frame_callback + ' }'
    code = r'''
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSocketClient
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceFrame
import java.net.*
import java.util.concurrent.*
import java.lang.ref.WeakReference
open class View { var background: Any? = null; interface OnAttachStateChangeListener { fun onViewAttachedToWindow(v:View); fun onViewDetachedFromWindow(v:View) } }
class OneShotPreDrawListener(val owner:Tiles,val action:()->Unit) { fun removeListener(){owner.draws.remove(this)} }
open class Activity { open fun onPause(){}; open fun onResume(){} }
class Screen { fun setActive(v:Boolean){} }
class Main { val posts=LinkedBlockingQueue<()->Unit>(); fun post(action:()->Unit){posts.add(action)}; fun runNext(){checkNotNull(posts.poll(2,TimeUnit.SECONDS)){"No frame callback"}.invoke()}; fun removeCallbacksAndMessages(v:Any?){posts.clear()} }
object LocalBroadcastManager { fun getInstance(c:Any)=this; fun registerReceiver(r:Any,f:Any){}; fun unregisterReceiver(r:Any){} }
class IntentFilter(s:String)
object UIManager { val ACTION_TERMUX_RESULT="result" }
class TextView { var typeface: Any? = null; var textSize=0f; var background: Any?=null; fun setTextColor(v:Int){} }
class Input { var typeface:Any?=null; var textSize=0f; val text="editor draft"; val selectionStart=2; val selectionEnd=5; fun setTextColor(v:Int){} }
class Root { val border=View(); val pageControls=(6..8).associateWith{TextView()}; @Suppress("UNCHECKED_CAST") fun <T> findViewById(id:Int):T=(pageControls[id] ?: border) as T }
class Grid { var width=800; var height=480; var size=12f; var frame="retained frame"; fun setFrame(body:String?,cols:Int,rows:Int,x:Int?,y:Int?){frame=body.orEmpty()}; fun characterWidth()=size*5/6; fun lineHeight()=(size*5/3).toInt(); fun setTerminalTypeface(v:Any?){}; fun setTerminalTextSizeSp(v:Float){size=v}; fun updateThemeColors(a:Int,b:Int,c:Int){} }
// Match AndroidX OneShot: detach removes pending listeners WITHOUT running their actions.
class Tiles : View() {
 var isAttachedToWindow=true; val draws=mutableListOf<OneShotPreDrawListener>(); val listeners=mutableListOf<View.OnAttachStateChangeListener>()
 fun addOnAttachStateChangeListener(l:View.OnAttachStateChangeListener){listeners.add(l)}
 fun doOnPreDraw(action:(Tiles)->Unit):OneShotPreDrawListener=OneShotPreDrawListener(this){action(this)}.also{draws.add(it)}
 fun detach(){isAttachedToWindow=false; draws.toList().forEach{it.removeListener()};listeners.toList().forEach{it.onViewDetachedFromWindow(this)}}
 fun attach(){isAttachedToWindow=true;listeners.toList().forEach{it.onViewAttachedToWindow(this)}}
 fun draw(){if(!isAttachedToWindow)return;val pending=draws.toList();pending.forEach{it.removeListener();it.action()}}
}
object Tuils { fun getTypeface(c:Any)="font" }
object LauncherFontScale { var size=12f; fun terminalTextSizeSp()=size }
object AppearanceSettings { fun outputHeaderTextSize()=11 }
object FrameTarget { val OVERLAYS=1 }
object TerminalBorderRuntime { fun tabDrawable(c:Any,v:Int,t:Int)=v; fun bind(v:View,t:TextView){} }
object R { object id { const val termux_workspace_border=1; const val termux_workspace_home=2; const val termux_workspace_new=3; const val termux_workspace_ime=4; const val termux_workspace_layout=5; const val termux_workspace_page_previous=6; const val termux_workspace_page_position=7; const val termux_workspace_page_next=8 }; object drawable { const val ic_toolbar_home_24=1; const val ic_workspace_add_24=2; const val ic_workspace_keyboard_24=3; const val ic_workspace_layout_24=4 }; object string { const val workspace_home=1; const val workspace_new=2; const val workspace_ime=3; const val workspace_layout=4; const val workspace_bridge_input_not_sent=5; const val workspace_bridge_ready=6 } }
enum class Theme { workspace_panel_background_color,workspace_panel_text_color,workspace_header_background_color,workspace_output_background_color,workspace_output_text_color,workspace_panel_border_color,workspace_input_text_color,workspace_control_text_color }
object RetuiThemeBridge { fun sendToKeyboard(c:Any,i:Input,label:String,mode:String){} }
class Selection { val focused=1; val focusedTarget get()=targets[focused]; val targets=mutableListOf<String?>("@1","@2"); var visibleTiles=listOf(0,1); fun select(tile:Int,target:String?){targets[tile]=target} }
class Harness(val clients:Array<TermuxWorkspaceSocketClient?>) : Activity() {
 var geometryCaptureListener:OneShotPreDrawListener?=null
 var helperUpdating=false
 fun styleHelperPrompt(){}
 val screen=Screen(); var foreground:WeakReference<Harness>?=null;var generation=0;var resultLabel:String?=null;var bootstrap:Any?=null;val main=Main();val receiver=Any();var isFinishing=false
 val frames=arrayOfNulls<TermuxWorkspaceFrame>(2);var windows=emptyList<Pair<String,String>>();var pendingRuns=0
 fun closeTile(tile:Int){clients[tile]?.close();clients[tile]=null;lastGeometry[tile]=null};fun persist(){};fun runPendingCommand(){pendingRuns++};fun startBridge(){};fun reconcileWindows(){}
 val root=Root(); val title=TextView(); val input=Input(); val tiles=Tiles(); val panels=arrayOf(View(),View()); val grids=arrayOf(Grid(),Grid()); val labels=arrayOf(TextView(),TextView()); val selection=Selection()
 val creating=BooleanArray(2); val creationSeeds=arrayOfNulls<String>(2); var visible=true; val lastGeometry=arrayOfNulls<Pair<Int,Int>>(2); var geometryCapturePending=false
 val localDraft="retained 😀 draft"; val pendingCommand="launch must-not-replay"; val modifiers=setOf("C")
 fun color(t:Theme)=t.ordinal; fun panelDrawable(t:Theme)=color(t); fun updateLabels(){}; fun bindKeys(){}; fun refreshBreathOutSettings(){}
 fun bindChrome(id:Int,d:Int,s:Int,a:()->Unit){}; fun finish(){}; fun newShell(t:Int){}; fun focusInput(v:Boolean){}; fun showLayoutChooser(){}
 fun status(s:String){check(s=="ready"){s}}; fun getString(id:Int)=if(id==6)"ready" else "input not sent"
''' + bodies + r'''
}
class Peer(target:String) : AutoCloseable {
 val gone=LinkedBlockingQueue<String>()
 var receiveFrame:(String)->Unit={};fun emit(raw:String){val s=checkNotNull(socket);s.getOutputStream().write(raw.toByteArray());s.getOutputStream().flush()}
 val packets=LinkedBlockingQueue<String>(); val ready=CountDownLatch(1);val initialFrame=CountDownLatch(1); val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")); var socket:Socket?=null
 val client=TermuxWorkspaceSocketClient(object:TermuxWorkspaceSocketClient.Listener {
  override fun onFrame(frame:String){receiveFrame(frame);initialFrame.countDown()}; override fun onStatus(status:String){ready.countDown()}; override fun onError(message:String){}; override fun onClosed(){}
  override fun onTargetGone(target:String?){gone.add(target ?: "empty")}
 })
 init {
  Thread { server.accept().use { s -> socket=s; val input=s.getInputStream().bufferedReader(); packets.add(input.readLine()); s.getOutputStream().write("__RETUI_SOCKET__ retui-bridge-socket-v4\n__RETUI_SOCKET_STATUS__ ok\n__RETUI_SESSION__ retui_workspace\n__RETUI_FRAME_BASE64__ eA==\n__RETUI_FRAME_END__\n".toByteArray());s.getOutputStream().flush(); try { while(true) packets.add(input.readLine() ?: break) } catch(e:java.io.IOException){} } }.apply {isDaemon=true;start()}
  client.connect(TermuxWorkspaceSocketClient.SOCKET_NAME,"fixture-token",80,24,"127.0.0.1",server.localPort,true,target,true)
  check(ready.await(5,TimeUnit.SECONDS));check(initialFrame.await(5,TimeUnit.SECONDS)); check(packets.poll(2,TimeUnit.SECONDS)=="HELLO fixture-token 80 24 STREAM "+java.util.Base64.getEncoder().encodeToString(target.toByteArray())+" NO_CREATE")
 }
 fun expect(line:String){val actual=packets.poll(2,TimeUnit.SECONDS);check(actual==line){"Expected $line, received $actual: font metrics changed at unchanged pixel bounds but tmux geometry stayed stale"}}
 fun quiet(){check(packets.poll(150,TimeUnit.MILLISECONDS)==null){"Duplicate capture or input mutation"}}
 override fun close(){client.close();socket?.close();server.close()}
}
fun main(){ Peer("@1").use { a -> Peer("@2").use { b ->
 val h=Harness(arrayOf(a.client,b.client)); val clients=h.clients.toList(); val before=h.grids.map{it.width to it.height}
 h.capture(0);h.capture(1);a.expect("CAPTURE 80 24");b.expect("CAPTURE 80 24")
 LauncherFontScale.size=19f;h.style();h.style();a.quiet();b.quiet()
 h.tiles.draw();val sizes=(0..1).map{h.geometry(it)};check(sizes.all{it != 80 to 24});check(before==h.grids.map{it.width to it.height})
 a.expect("CAPTURE ${sizes[0].first} ${sizes[0].second}");b.expect("CAPTURE ${sizes[1].first} ${sizes[1].second}")
 h.style();h.tiles.draw();a.quiet();b.quiet()
 // A simultaneous pixel layout change must use final bounds, once per tile.
 LauncherFontScale.size=20f;h.style();h.grids[0].width=900;h.tiles.draw();
 a.expect("CAPTURE ${h.geometry(0).first} ${h.geometry(0).second}");b.expect("CAPTURE ${h.geometry(1).first} ${h.geometry(1).second}");a.quiet();b.quiet()
 // Palette/repeated notifications and changed metrics at identical clamped geometry send nothing.
 h.grids.forEach{it.width=1;it.height=1};h.capture(0);h.capture(1);a.expect("CAPTURE 20 8");b.expect("CAPTURE 20 8")
 LauncherFontScale.size=18f;h.style();h.tiles.draw();a.quiet();b.quiet()
 // Hidden tiles, pending creation, paused activity and disconnected clients cannot resize.
 h.selection.visibleTiles=listOf(1);h.grids.forEach{it.width=800;it.height=480};h.creating[1]=true;h.style();h.tiles.draw();a.quiet();b.quiet()
 h.creating[1]=false;h.visible=false;h.style();h.tiles.draw();a.quiet();b.quiet()
 h.visible=true;h.clients[1]=null;h.style();h.tiles.draw();a.quiet();b.quiet();h.clients[1]=b.client
 // A real input operation already resizing the socket is not followed by a duplicate capture.
 h.selection.visibleTiles=listOf(0,1);h.style();for(tile in 0..1)h.mutation(tile){c,s -> c.typeInput("é",s.first,s.second)}
 a.expect("TYPE ${h.geometry(0).first} ${h.geometry(0).second} w6k=");b.expect("TYPE ${h.geometry(1).first} ${h.geometry(1).second} w6k=")
 h.style();h.tiles.draw();a.quiet();b.quiet()
 check(clients==h.clients.toList() && h.localDraft=="retained 😀 draft" && h.pendingCommand=="launch must-not-replay" && h.modifiers==setOf("C") && h.selection.targets==listOf("@1","@2") && h.selection.focused==1 && h.input.text=="editor draft" && h.input.selectionStart==2 && h.input.selectionEnd==5 && h.grids.all{it.frame=="retained frame"})
 h.installAttachListener()
 // Detach cancels OneShot without executing it; reattach must retry at final metrics.
 LauncherFontScale.size=21f;h.style();check(h.geometryCapturePending);h.tiles.detach()
 check(h.tiles.draws.isEmpty());check(!h.geometryCapturePending && h.geometryCaptureListener==null){"Detach cancelled OneShot but left geometry pending stuck"};a.quiet();b.quiet()
 LauncherFontScale.size=22f;h.style();check(h.tiles.draws.isEmpty()){"Detached style queued a listener"}
 h.tiles.attach();h.style();check(h.tiles.draws.size==1);h.tiles.draw()
 a.expect("CAPTURE ${h.geometry(0).first} ${h.geometry(0).second}");b.expect("CAPTURE ${h.geometry(1).first} ${h.geometry(1).second}");a.quiet();b.quiet()
 // Actual onPause must cancel even while attached; onResume/style schedules a fresh check.
 val paused=Harness(arrayOf(null,null));paused.installAttachListener();paused.style();check(paused.tiles.draws.size==1)
 paused.onPause();check(!paused.geometryCapturePending && paused.geometryCaptureListener==null && paused.tiles.draws.isEmpty()){"Pause retained a pending pre-draw"}
 paused.tiles.detach();paused.tiles.attach();check(paused.tiles.draws.isEmpty()){"Paused reattach scheduled geometry"}
 paused.onResume();check(paused.tiles.draws.size==1);paused.tiles.draw();check(!paused.geometryCapturePending)
 h.selection.visibleTiles=listOf(1);b.client.close();LauncherFontScale.size=17f;h.style();h.tiles.draw();a.quiet();b.quiet()
 println("PASS geometry/lifecycle: real Activity bodies + existing TCP sockets; settled font/layout resize, repeat/clamp/input dedup, hidden/creating/paused/disconnected guards; OneShot detach cancellation, visible reattach and pause/resume retry; drafts/selection/targets/frames retained")
 creationCompletion()
 exitEvents()
} } }
fun exitEvents(){Peer("@1").use { peer ->
 peer.emit("__RETUI_TARGET_GONE__ @1\n")
 check(peer.gone.poll(2,TimeUnit.SECONDS)=="@1"){"Missing targeted shell exit callback"}
 check(peer.client.connected);check(peer.client.newWindow(null,null,80,24));peer.expect("NEW 80 24 - -")
 peer.emit("__RETUI_TARGET_GONE__ -\n")
 check(peer.gone.poll(2,TimeUnit.SECONDS)=="empty"){"Missing last-shell exit callback"}
 println("PASS shell exit protocol: authenticated target-gone and empty-session events through production TCP reader; connection remains usable for explicit NEW")
} }
fun creationCompletion(){Peer("@1").use { peer ->
 LauncherFontScale.size=12f
 val h=Harness(arrayOf(null,peer.client));h.selection.visibleTiles=listOf(1);h.selection.targets[1]=null;h.creating[1]=true;h.style()
 val originalClient=h.clients[1];val bounds=h.grids[1].width to h.grids[1].height
 peer.receiveFrame={h.acceptFrame(1,peer.client,it)}
 h.mutation(1){c,size -> c.newWindow(null,null,size.first,size.second)};peer.expect("NEW 80 24 - -")
 LauncherFontScale.size=19f;h.style();h.tiles.draw();peer.quiet();check(h.creating[1])
 fun frame(target:String)="__RETUI_TARGET__ $target\n__RETUI_COLS__ 80\n__RETUI_ROWS__ 24\n__RETUI_FRAME_BASE64__ bmV3IHNoZWxs\n__RETUI_FRAME_END__\n"
 // The old/source target cannot complete creation or trigger resize.
 peer.emit(frame("@1"));h.main.runNext();check(h.creating[1]);check(h.selection.targets[1]==null);check(h.tiles.draws.isEmpty());peer.quiet()
 peer.emit(frame("@3"));h.main.runNext();check(!h.creating[1]);check(h.selection.targets==listOf("@1","@3"));check(h.frames[1]?.target=="@3" && h.grids[1].frame=="new shell")
 check(bounds==h.grids[1].width to h.grids[1].height);peer.quiet();h.tiles.draw()
 peer.expect("CAPTURE ${h.geometry(1).first} ${h.geometry(1).second}");peer.quiet()
 peer.emit(frame("@3"));h.main.runNext();h.tiles.draw();h.style();h.tiles.draw();peer.quiet()
 check(h.clients[1]===originalClient && h.selection.focused==1 && h.localDraft=="retained 😀 draft" && h.pendingCommand=="launch must-not-replay" && h.modifiers==setOf("C") && h.input.text=="editor draft" && h.input.selectionStart==2 && h.input.selectionEnd==5)
 check(h.pendingRuns==2){"Resize replayed pending commands"}
 // Paused/stale transport frames still cannot complete creation or change selection.
 h.creating[1]=true;h.visible=false;peer.emit(frame("@4"));h.main.runNext();check(h.creating[1] && h.selection.targets[1]=="@3");peer.quiet()
 h.visible=true;h.clients[1]=null;peer.emit(frame("@4"));h.main.runNext();check(h.creating[1] && h.selection.targets[1]=="@3");peer.quiet();h.clients[1]=peer.client
 // Creation with already queued/current geometry needs no extra CAPTURE.
 h.mutation(1){c,size -> c.newWindow(null,null,size.first,size.second)};peer.expect("NEW ${h.geometry(1).first} ${h.geometry(1).second} - -")
 peer.emit(frame("@4"));h.main.runNext();check(!h.creating[1] && h.selection.targets[1]=="@4");h.tiles.draw();peer.quiet();check(h.pendingRuns==3)
 println("PASS creation completion: real accepted-frame callback/parser/TCP; NEW at old dimensions, font change skipped in flight, rejected old target, accepted new target receives one deferred CAPTURE on same socket; no duplicate commands/reselect/reconnect/draft changes")
} }
'''
    net = '''package android.net
class LocalSocket { var soTimeout=0; val inputStream:java.io.InputStream get()=error("host"); val outputStream:java.io.OutputStream get()=error("host"); fun connect(a:LocalSocketAddress){throw java.io.IOException("Use host TCP")}; fun close(){} }
class LocalSocketAddress(val name:String,val namespace:Namespace) { enum class Namespace { ABSTRACT } }
'''
    b64 = '''package android.util
object Base64 { const val NO_WRAP=2; const val DEFAULT=0; fun encodeToString(v:ByteArray,flags:Int)=java.util.Base64.getEncoder().encodeToString(v); fun decode(v:String,flags:Int)=java.util.Base64.getDecoder().decode(v) }
'''
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    jars = []
    for pattern in ('org.jetbrains.kotlin/kotlin-compiler-embeddable/2.2.10/**/*.jar', 'org.jetbrains.kotlin/kotlin-stdlib/2.2.10/**/*.jar', 'org.jetbrains.kotlin/kotlin-script-runtime/2.2.10/**/*.jar', 'org.jetbrains.kotlin/kotlin-reflect/**/*.jar', 'org.jetbrains.intellij.deps/trove4j/**/*.jar', 'org.jetbrains/annotations/**/*.jar', 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/**/*.jar'):
        found = sorted(p for p in cache.glob(pattern) if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
        if found:
            jars.append(str(found[-1]))
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else shutil.which('java')
    if not java or not jars:
        raise SystemExit('Java and Gradle-cached Kotlin compiler required')
    cp = os.pathsep.join(jars)
    with tempfile.TemporaryDirectory(prefix='termux-geometry-') as directory:
        out = Path(directory)
        files = []
        for name, text in (('GeometrySlice.kt', code), ('Net.kt', net), ('Base64.kt', b64)):
            path = out / name
            path.write_text(text)
            files.append(str(path))
        client = ROOT / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceSocketClient.kt'
        frame = SOURCE.with_name('TermuxWorkspaceFrame.kt')
        subprocess.run([java, '-cp', cp, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-nowarn', '-classpath', cp, '-d', str(out / 'classes'), *files, str(client), str(frame)], check=True)
        subprocess.run([java, '-cp', str(out / 'classes') + os.pathsep + cp, 'GeometrySliceKt'], check=True)


if __name__ == '__main__':
    main()
