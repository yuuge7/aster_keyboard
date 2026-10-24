import 'package:aster_keyboard/app/aster_app.dart';
import 'package:aster_keyboard/services/keyboard_bridge.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await (FontLoader('Roboto')
      ..addFont(rootBundle.load('assets/fonts/roboto-regular.ttf'))
      ..addFont(rootBundle.load('assets/fonts/roboto-bold.ttf'))).load();
    await (FontLoader('MaterialIcons')
      ..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  final calls = <String>[];
  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(KeyboardBridge.channel, (call) async {
      calls.add(call.method);
      if (call.method == 'getStatus') {
        return {'enabled': true, 'selected': true, 'apiLevel': 36};
      }
      return null;
    });
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(KeyboardBridge.channel, null);
  });

  testWidgets('Reflects native activation and opens the real typing screen', (tester) async {
    tester.view.physicalSize = const Size(430, 932);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    expect(find.text('Aster is ready'), findsOneWidget);
    expect(calls, contains('getStatus'));
    await tester.tap(find.text('Try it'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('messageField')), 'Hello from Aster');
    expect(find.text('Hello from Aster'), findsOneWidget);
    await tester.tap(find.text('Privacy'));
    await tester.pumpAndSettle();
    expect(find.text('Offline by design'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Setup fits a small phone and renders the setup screenshot', (tester) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(find.byType(AsterApp), matchesGoldenFile('goldens/setup.png'));
  });

  testWidgets('A platform failure leaves a usable recovery screen', (tester) async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(KeyboardBridge.channel, (call) async {
      throw PlatformException(code: 'unavailable');
    });
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not check Android'), findsOneWidget);
    expect(find.byTooltip('Refresh keyboard status'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Setup and privacy remain usable in landscape', (tester) async {
    tester.view.physicalSize = const Size(740, 360);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Privacy'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
