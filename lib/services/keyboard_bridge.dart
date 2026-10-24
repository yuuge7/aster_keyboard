import 'package:flutter/services.dart';

import 'keyboard_settings.dart';

class KeyboardStatus {
  const KeyboardStatus({
    this.enabled = false,
    this.selected = false,
    this.apiLevel,
  });
  final bool enabled;
  final bool selected;
  final int? apiLevel;
  factory KeyboardStatus.fromMap(Map<Object?, Object?> value) => KeyboardStatus(
    enabled: value['enabled'] == true,
    selected: value['selected'] == true,
    apiLevel: value['apiLevel'] as int?,
  );
}

class KeyboardBridge {
  static const channel = MethodChannel('app.aster/keyboard');
  void onSettingsRequested(void Function()? callback) {
    channel.setMethodCallHandler(
      callback == null
          ? null
          : (call) async {
              if (call.method == 'openSettings') callback();
            },
    );
  }

  Future<KeyboardStatus> status() async => KeyboardStatus.fromMap(
    await channel.invokeMapMethod<Object?, Object?>('getStatus') ?? {},
  );
  Future<void> enable() => channel.invokeMethod<void>('openInputSettings');
  Future<void> choose() => channel.invokeMethod<void>('showInputPicker');
  Future<void> nativeTests() => channel.invokeMethod<void>('openEditorTests');
  Future<KeyboardSettings> settings() => _settings('getSettings');
  Future<KeyboardSettings> update(Map<String, Object?> patch) =>
      _settings('updateSettings', patch);
  Future<KeyboardSettings> chooseWallpaper() => _settings('chooseWallpaper');
  Future<KeyboardSettings> removeWallpaper() => _settings('removeWallpaper');
  Future<bool> consumeSettingsRequest() async =>
      await channel.invokeMethod<bool>('consumeSettingsRequest') ?? false;
  Future<KeyboardSettings> _settings(
    String method, [
    Map<String, Object?>? arguments,
  ]) async {
    final result = await channel.invokeMapMethod<String, Object?>(
      method,
      arguments,
    );
    return KeyboardSettings(result ?? const {});
  }
}
