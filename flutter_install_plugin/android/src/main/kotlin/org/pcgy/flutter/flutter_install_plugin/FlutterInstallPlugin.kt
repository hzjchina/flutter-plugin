package org.pcgy.flutter.flutter_install_plugin


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.io.FileNotFoundException

/** FlutterInstallPlugin */
class FlutterInstallPlugin(private val registrar: Registrar): MethodCallHandler {

  companion object {
    private const val installRequestCode = 1234
    private var apkFile: File? = null
    private var appId: String? = null

    @JvmStatic
    fun registerWith(registrar: Registrar): Unit {
      val channel = MethodChannel(registrar.messenger(), "flutter_install_plugin")
      val installPlugin = FlutterInstallPlugin(registrar)
      channel.setMethodCallHandler(installPlugin)
      registrar.addActivityResultListener { requestCode, resultCode, intent ->
//        Log.d(
//                "ActivityResultListener",
//                "requestCode=$requestCode, resultCode = $resultCode, intent = $intent"
//        )
        if (resultCode == Activity.RESULT_OK && requestCode == installRequestCode) {
          installPlugin.install24(registrar.context(), apkFile, appId)
          true
        } else

          false
      }
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "installApk" -> {
        val filePath = call.argument<String>("filePath")
        val appId = call.argument<String>("appId")
        //Log.d("android plugin", "installApk $filePath $appId")
        try {
          installApk(filePath, appId)
          result.success("Success")
        } catch (e: Throwable) {
          result.error(e.javaClass.simpleName, e.message, null)
        }
      }

      "gotoAndroidMarket" -> {
        try {
          val marketPackageName = call.argument<String>("marketPackageName")
//          val marketClassName = call.argument<String>("marketClassName")
          //Log.d("android plugin", "gotoAndroidMarket $marketPackageName ")
          toMarket(marketPackageName)
        } catch (e: Throwable) {
          result.error(e.javaClass.simpleName, e.message, null)
        }
      }
      else -> result.notImplemented()
    }
  }

  private fun toMarket(marketPackageName: String?) {
    val activity: Activity =
            registrar.activity() ?: throw NullPointerException("context is null!")
    var packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
    try {
      /**
      * ???????????????????????? ?????????????????????????????????????????????
      * */
      val uri = Uri.parse("market://details?id=${packageInfo.packageName}")
      val goToMarket = Intent(Intent.ACTION_VIEW, uri)
      goToMarket.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (marketPackageName != null && marketPackageName.isNotEmpty()) {
        goToMarket.setPackage(marketPackageName)
      }
      activity.startActivity(goToMarket)
    }catch (e: Throwable){
      /**
       * ??????Google play ??????
       */
      val uri2 = Uri.parse("https://play.google.com/store/apps/details?id=${packageInfo.packageName}")
      val goToMarket2 = Intent(Intent.ACTION_VIEW, uri2)
      goToMarket2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      activity.startActivity(goToMarket2)
    }
  }

  private fun installApk(filePath: String?, currentAppId: String?) {
    if (filePath == null) throw NullPointerException("fillPath is null!")
    val activity: Activity =
            registrar.activity() ?: throw NullPointerException("context is null!")

    val file = File(filePath)
    if (!file.exists()) throw FileNotFoundException("$filePath is not exist! or check permission")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (canRequestPackageInstalls(activity)) install24(activity, file, currentAppId)
      else {
        showSettingPackageInstall(activity)
        apkFile = file
        appId = currentAppId
      }
    } else {
      installBelow24(activity, file)
    }
  }


  private fun showSettingPackageInstall(activity: Activity) { // todo to test with android 26
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      //Log.d("SettingPackageInstall", ">= Build.VERSION_CODES.O")
      val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
      intent.data = Uri.parse("package:" + activity.packageName)
      activity.startActivityForResult(intent, installRequestCode)
    } else {
      throw RuntimeException("VERSION.SDK_INT < O")
    }

  }

  private fun canRequestPackageInstalls(activity: Activity): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()
  }

  private fun installBelow24(context: Context, file: File?) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val uri = Uri.fromFile(file)
    intent.setDataAndType(uri, "application/vnd.android.package-archive")
    context.startActivity(intent)
  }

  /**
   * android24??????????????????????????? ContentProvider ????????????Uri???
   * ??????????????????AndroidManifest.xml ???????????? provider ?????????
   * ????????????????????????????????? res/xml/provider_path.xml
   * ???android 6.0 ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
   * ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
   */
  private fun install24(context: Context?, file: File?, appId: String?) {
    if (context == null) throw NullPointerException("context is null!")
    if (file == null) throw NullPointerException("file is null!")
    if (appId == null) throw NullPointerException("appId is null!")
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val uri: Uri = FileProvider.getUriForFile(context, "$appId.fileProvider.install", file)
    intent.setDataAndType(uri, "application/vnd.android.package-archive")
    context.startActivity(intent)
  }


}
