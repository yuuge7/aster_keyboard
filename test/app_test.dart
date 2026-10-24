import 'package:aster_keyboard/app/aster_app.dart';
import 'package:aster_keyboard/services/keyboard_bridge.dart';
import 'package:aster_keyboard/services/keyboard_settings.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await (FontLoader('Roboto')
          ..addFont(rootBundle.load('assets/fonts/roboto-regular.ttf'))
          ..addFont(rootBundle.load('assets/fonts/roboto-bold.ttf')))
        .load();
    await (FontLoader(
      'MaterialIcons',
    )..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  final calls = <String>[];
  var preferences = <String, Object?>{};
  setUp(() {
    calls.clear();
    preferences = {...KeyboardSettings.defaults, 'appVersion': '1.2.0'};
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(KeyboardBridge.channel, (call) async {
          calls.add(call.method);
          if (call.method == 'getStatus') {
            return {'enabled': true, 'selected': true, 'apiLevel': 36};
          }
          if (call.method == 'getSettings' ||
              call.method == 'chooseWallpaper') {
            return preferences;
          }
          if (call.method == 'consumeSettingsRequest') return false;
          if (call.method == 'updateSettings') {
            preferences.addAll(
              Map<String, Object?>.from(call.arguments as Map),
            );
            return preferences;
          }
          return null;
        });
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(KeyboardBridge.channel, null);
  });

  testWidgets('Reflects native activation and opens the real typing screen', (
    tester,
  ) async {
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
    await tester.enterText(
      find.byKey(const Key('messageField')),
      'Hello from Aster',
    );
    expect(find.text('Hello from Aster'), findsOneWidget);
    await tester.tap(find.text('Privacy'));
    await tester.pumpAndSettle();
    expect(find.text('Offline by design'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Setup fits a small phone and renders the setup screenshot', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byType(AsterApp),
      matchesGoldenFile('goldens/setup.png'),
    );
  });

  testWidgets('A platform failure leaves a usable recovery screen', (
    tester,
  ) async {
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

  testWidgets(
    'Dark theme and typing controls persist through the native bridge',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(const AsterApp());
      await tester.pumpAndSettle();
      await tester.tap(find.text('Settings'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Dark'));
      await tester.tap(find.text('Dark'));
      await tester.pumpAndSettle();
      expect(preferences['theme'], 'dark');
      expect(
        Theme.of(tester.element(find.text('Dark'))).brightness,
        Brightness.dark,
      );
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('setting-autoCapitalization')),
        300,
        scrollable: find
            .descendant(
              of: find.byKey(const PageStorageKey('keyboardSettings')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.tap(
        find.byKey(const ValueKey('setting-autoCapitalization')),
      );
      await tester.pumpAndSettle();
      expect(preferences['autoCapitalization'], false);
      expect(calls, contains('updateSettings'));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Wallpaper cancellation leaves settings intact', (tester) async {
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('chooseWallpaper')));
    await tester.tap(find.byKey(const Key('chooseWallpaper')));
    await tester.pumpAndSettle();
    expect(calls, contains('chooseWallpaper'));
    expect(find.text('Remove wallpaper'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Settings preview fits a small phone in dark mode', (
    tester,
  ) async {
    preferences['theme'] = 'dark';
    preferences['language'] = 'ro';
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byType(AsterApp),
      matchesGoldenFile('goldens/settings-dark.png'),
    );
  });

  testWidgets('Rejected settings stay unchanged and show a recoverable error', (
    tester,
  ) async {
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(KeyboardBridge.channel, (call) async {
          if (call.method == 'updateSettings') {
            throw PlatformException(
              code: 'save_failed',
              message: 'Settings could not be saved. Try again.',
            );
          }
          return preferences;
        });
    await tester.ensureVisible(find.text('Dark'));
    await tester.tap(find.text('Dark'));
    await tester.pumpAndSettle();
    expect(preferences['theme'], 'system');
    expect(
      find.text('Settings could not be saved. Try again.'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Personal dictionary validates words and saves explicit additions',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(const AsterApp());
      await tester.pumpAndSettle();
      await tester.tap(find.text('Settings'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Personal dictionary'),
        400,
        scrollable: find
            .descendant(
              of: find.byKey(const PageStorageKey('keyboardSettings')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.tap(find.text('Personal dictionary'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('dictionary-en')),
        'two words',
      );
      await tester.tap(find.text('Save words'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Only letters'), findsOneWidget);
      await tester.enterText(
        find.byKey(const ValueKey('dictionary-en')),
        'Alexandra\nȘtefan',
      );
      await tester.tap(find.text('Save words'));
      await tester.pumpAndSettle();
      expect(preferences['personalWordsEn'], 'Alexandra\nȘtefan');
      expect(find.byType(AlertDialog), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('A fresh companion session reloads saved appearance', (
    tester,
  ) async {
    preferences['theme'] = 'dark';
    preferences['language'] = 'ro';
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpAndSettle();
    await tester.pumpWidget(const AsterApp());
    await tester.pumpAndSettle();
    expect(
      tester.widget<MaterialApp>(find.byType(MaterialApp)).themeMode,
      ThemeMode.dark,
    );
    expect(calls.where((call) => call == 'getSettings').length, 2);
    expect(tester.takeException(), isNull);
  });
}
