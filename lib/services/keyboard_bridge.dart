import 'package:flutter/services.dart';

class KeyboardStatus {
  const KeyboardStatus({this.enabled = false, this.selected = false, this.apiLevel});
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
  Future<KeyboardStatus> status() async => KeyboardStatus.fromMap(
    await channel.invokeMapMethod<Object?, Object?>('getStatus') ?? {},
  );
  Future<void> enable() => channel.invokeMethod<void>('openInputSettings');
  Future<void> choose() => channel.invokeMethod<void>('showInputPicker');
  Future<void> nativeTests() => channel.invokeMethod<void>('openEditorTests');
}
