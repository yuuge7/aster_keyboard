import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'app/aster_app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  LicenseRegistry.addLicense(() async* {
    final text = await rootBundle.loadString('assets/fonts/roboto_license.txt');
    yield LicenseEntryWithLineBreaks(['Roboto'], text);
    final words = await rootBundle.loadString('assets/licenses/wordlists.txt');
    yield LicenseEntryWithLineBreaks(['FrequencyWords / OpenSubtitles'], words);
  });
  runApp(const AsterApp());
}
