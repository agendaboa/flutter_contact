import 'package:flexidate/flexidate.dart';
import 'package:logging/logging.dart';

final _log = Logger("dateComponents");

const _deprecationMessage = 'Use FlexiDate instead.';

@Deprecated(_deprecationMessage)
class DateComponents extends FlexiDateData {
  @Deprecated(_deprecationMessage)
  DateComponents({super.day, super.month, super.year});

  @Deprecated(_deprecationMessage)
  static DateComponents fromDateTime(DateTime dateTime) => DateComponents(
      day: dateTime.day, month: dateTime.month, year: dateTime.year);

  @Deprecated(_deprecationMessage)
  factory DateComponents.now() => DateComponents.fromDateTime(DateTime.now());

  @Deprecated(_deprecationMessage)
  DateComponents.fromMap(Map toParse)
      : super(
            day: _tryParseInt(toParse[kday]),
            month: _tryParseInt(toParse[kmonth]),
            year: _tryParseInt(toParse[kyear]));

  @Deprecated(_deprecationMessage)
  static DateComponents? _fromFlexiDate(FlexiDate? flexiDate) {
    if (flexiDate == null) return null;
    return DateComponents(
        day: flexiDate.day, month: flexiDate.month, year: flexiDate.year);
  }

  @Deprecated(_deprecationMessage)
  static DateComponents? tryFrom(dynamic input) =>
      _fromFlexiDate(FlexiDate.tryFrom(input));

  @Deprecated(_deprecationMessage)
  static DateComponents from(dynamic input) =>
      _fromFlexiDate(FlexiDate.from(input))!;

  @Deprecated(_deprecationMessage)
  DateComponents copy() {
    return DateComponents(day: day, month: month, year: year);
  }

  @Deprecated(_deprecationMessage)
  static DateComponents? tryParse(String input) {
    try {
      return _fromFlexiDate(FlexiDate.parse(input))!;
    } catch (e) {
      _log.finer("Date parse error: $e");
      return null;
    }
  }

  @Deprecated(_deprecationMessage)
  static DateComponents parse(String toParse) =>
      _fromFlexiDate(FlexiDate.parse(toParse))!;

  @Deprecated(_deprecationMessage)
  static DateComponents? fromJson(dynamic json) =>
      _fromFlexiDate(FlexiDate.fromJson(json));
}

int? _tryParseInt(dynamic dyn) {
  if (dyn == null) return null;
  return int.tryParse("$dyn");
}
