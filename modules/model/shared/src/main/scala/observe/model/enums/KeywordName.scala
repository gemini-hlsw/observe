// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

/**
 * Enumerated type for Fits Keyword names.
 * @group Enumerations (Generated)
 */
enum KeywordName(
  val tag:  String,
  val name: String
) derives Enumerated {

  /** @group Constructors */
  case INSTRUMENT extends KeywordName("INSTRUMENT", "INSTRUME")

  /** @group Constructors */
  case SW_NAME extends KeywordName("SW_NAME", "SW_NAME")

  case SW_VER extends KeywordName("SW_VER", "SW_VER")

  /** @group Constructors */
  case OBJECT extends KeywordName("OBJECT", "OBJECT")

  /** @group Constructors */
  case OBSTYPE extends KeywordName("OBSTYPE", "OBSTYPE")

  /** @group Constructors */
  case OBSCLASS extends KeywordName("OBSCLASS", "OBSCLASS")

  /** @group Constructors */
  case GEMPRGID extends KeywordName("GEMPRGID", "GEMPRGID")

  /** @group Constructors */
  case OBSID extends KeywordName("OBSID", "OBSID")

  /** @group Constructors */
  case DATALAB extends KeywordName("DATALAB", "DATALAB")

  /** @group Constructors */
  case OBSERVER extends KeywordName("OBSERVER", "OBSERVER")

  /** @group Constructors */
  case OBSERVAT extends KeywordName("OBSERVAT", "OBSERVAT")

  /** @group Constructors */
  case TELESCOP extends KeywordName("TELESCOP", "TELESCOP")

  /** @group Constructors */
  case PARALLAX extends KeywordName("PARALLAX", "PARALLAX")

  /** @group Constructors */
  case RADVEL extends KeywordName("RADVEL", "RADVEL")

  /** @group Constructors */
  case EPOCH extends KeywordName("EPOCH", "EPOCH")

  /** @group Constructors */
  case EQUINOX extends KeywordName("EQUINOX", "EQUINOX")

  /** @group Constructors */
  case TRKEQUIN extends KeywordName("TRKEQUIN", "TRKEQUIN")

  /** @group Constructors */
  case SSA extends KeywordName("SSA", "SSA")

  /** @group Constructors */
  case RA extends KeywordName("RA", "RA")

  /** @group Constructors */
  case DEC extends KeywordName("DEC", "DEC")

  /** @group Constructors */
  case ELEVATIO extends KeywordName("ELEVATIO", "ELEVATIO")

  /** @group Constructors */
  case AZIMUTH extends KeywordName("AZIMUTH", "AZIMUTH")

  /** @group Constructors */
  case CRPA extends KeywordName("CRPA", "CRPA")

  /** @group Constructors */
  case HA extends KeywordName("HA", "HA")

  /** @group Constructors */
  case LT extends KeywordName("LT", "LT")

  /** @group Constructors */
  case TRKFRAME extends KeywordName("TRKFRAME", "TRKFRAME")

  /** @group Constructors */
  case DECTRACK extends KeywordName("DECTRACK", "DECTRACK")

  /** @group Constructors */
  case TRKEPOCH extends KeywordName("TRKEPOCH", "TRKEPOCH")

  /** @group Constructors */
  case RATRACK extends KeywordName("RATRACK", "RATRACK")

  /** @group Constructors */
  case FRAME extends KeywordName("FRAME", "FRAME")

  /** @group Constructors */
  case PMDEC extends KeywordName("PMDEC", "PMDEC")

  /** @group Constructors */
  case PMRA extends KeywordName("PMRA", "PMRA")

  /** @group Constructors */
  case WAVELENG extends KeywordName("WAVELENG", "WAVELENG")

  /** @group Constructors */
  case RAWIQ extends KeywordName("RAWIQ", "RAWIQ")

  /** @group Constructors */
  case RAWCC extends KeywordName("RAWCC", "RAWCC")

  /** @group Constructors */
  case RAWWV extends KeywordName("RAWWV", "RAWWV")

  /** @group Constructors */
  case RAWBG extends KeywordName("RAWBG", "RAWBG")

  /** @group Constructors */
  case RAWDIQ extends KeywordName("RAWDIQ", "RAWDIQ")

  /** @group Constructors */
  case RAWDCC extends KeywordName("RAWDCC", "RAWDCC")

  /** @group Constructors */
  case RAWDWV extends KeywordName("RAWDWV", "RAWDWV")

  /** @group Constructors */
  case RAWDBG extends KeywordName("RAWDBG", "RAWDBG")

  /** @group Constructors */
  case RAWPIREQ extends KeywordName("RAWPIREQ", "RAWPIREQ")

  /** @group Constructors */
  case RAWGEMQA extends KeywordName("RAWGEMQA", "RAWGEMQA")

  /** @group Constructors */
  case CGUIDMOD extends KeywordName("CGUIDMOD", "CGUIDMOD")

  /** @group Constructors */
  case UT extends KeywordName("UT", "UT")

  /** @group Constructors */
  case DATE extends KeywordName("DATE", "DATE")

  /** @group Constructors */
  case M2BAFFLE extends KeywordName("M2BAFFLE", "M2BAFFLE")

  /** @group Constructors */
  case M2CENBAF extends KeywordName("M2CENBAF", "M2CENBAF")

  /** @group Constructors */
  case ST extends KeywordName("ST", "ST")

  /** @group Constructors */
  case XOFFSET extends KeywordName("XOFFSET", "XOFFSET")

  /** @group Constructors */
  case YOFFSET extends KeywordName("YOFFSET", "YOFFSET")

  /** @group Constructors */
  case POFFSET extends KeywordName("POFFSET", "POFFSET")

  /** @group Constructors */
  case QOFFSET extends KeywordName("QOFFSET", "QOFFSET")

  /** @group Constructors */
  case RAOFFSET extends KeywordName("RAOFFSET", "RAOFFSET")

  /** @group Constructors */
  case DECOFFSE extends KeywordName("DECOFFSE", "DECOFFSE")

  /** @group Constructors */
  case RATRGOFF extends KeywordName("RATRGOFF", "RATRGOFF")

  /** @group Constructors */
  case DECTRGOF extends KeywordName("DECTRGOF", "DECTRGOF")

  /** @group Constructors */
  case PA extends KeywordName("PA", "PA")

  /** @group Constructors */
  case IAA extends KeywordName("IAA", "IAA")

  /** @group Constructors */
  case SFRT2 extends KeywordName("SFRT2", "SFRT2")

  /** @group Constructors */
  case SFTILT extends KeywordName("SFTILT", "SFTILT")

  /** @group Constructors */
  case SFLINEAR extends KeywordName("SFLINEAR", "SFLINEAR")

  /** @group Constructors */
  case AOFOLD extends KeywordName("AOFOLD", "AOFOLD")

  /** @group Constructors */
  case PWFS1_ST extends KeywordName("PWFS1_ST", "PWFS1_ST")

  /** @group Constructors */
  case PWFS2_ST extends KeywordName("PWFS2_ST", "PWFS2_ST")

  /** @group Constructors */
  case OIWFS_ST extends KeywordName("OIWFS_ST", "OIWFS_ST")

  /** @group Constructors */
  case AOWFS_ST extends KeywordName("AOWFS_ST", "AOWFS_ST")

  /** @group Constructors */
  case SCIBAND extends KeywordName("SCIBAND", "SCIBAND")

  /** @group Constructors */
  case NUMREQTW extends KeywordName("NUMREQTW", "NUMREQTW")

  /** @group Constructors */
  case REQIQ extends KeywordName("REQIQ", "REQIQ")

  /** @group Constructors */
  case REQCC extends KeywordName("REQCC", "REQCC")

  /** @group Constructors */
  case REQBG extends KeywordName("REQBG", "REQBG")

  /** @group Constructors */
  case REQWV extends KeywordName("REQWV", "REQWV")

  /** @group Constructors */
  case REQDIQ extends KeywordName("REQDIQ", "REQDIQ")

  /** @group Constructors */
  case REQDCC extends KeywordName("REQDCC", "REQDCC")

  /** @group Constructors */
  case REQDBG extends KeywordName("REQDBG", "REQDBG")

  /** @group Constructors */
  case REQDWV extends KeywordName("REQDWV", "REQDWV")

  /** @group Constructors */
  case REQMAXAM extends KeywordName("REQMAXAM", "REQMAXAM")

  /** @group Constructors */
  case REQMAXHA extends KeywordName("REQMAXHA", "REQMAXHA")

  /** @group Constructors */
  case REQMINAM extends KeywordName("REQMINAM", "REQMINAM")

  /** @group Constructors */
  case REQMINHA extends KeywordName("REQMINHA", "REQMINHA")

  /** @group Constructors */
  case OIARA extends KeywordName("OIARA", "OIARA")

  /** @group Constructors */
  case OIADEC extends KeywordName("OIADEC", "OIADEC")

  /** @group Constructors */
  case OIARV extends KeywordName("OIARV", "OIARV")

  /** @group Constructors */
  case OIAWAVEL extends KeywordName("OIAWAVEL", "OIAWAVEL")

  /** @group Constructors */
  case OIAEPOCH extends KeywordName("OIAEPOCH", "OIAEPOCH")

  /** @group Constructors */
  case OIAEQUIN extends KeywordName("OIAEQUIN", "OIAEQUIN")

  /** @group Constructors */
  case OIAFRAME extends KeywordName("OIAFRAME", "OIAFRAME")

  /** @group Constructors */
  case OIAOBJEC extends KeywordName("OIAOBJEC", "OIAOBJEC")

  /** @group Constructors */
  case OIAPMDEC extends KeywordName("OIAPMDEC", "OIAPMDEC")

  /** @group Constructors */
  case OIAPMRA extends KeywordName("OIAPMRA", "OIAPMRA")

  /** @group Constructors */
  case OIAPARAL extends KeywordName("OIAPARAL", "OIAPARAL")

  /** @group Constructors */
  case OIFOCUS extends KeywordName("OIFOCUS", "OIFOCUS")

  /** @group Constructors */
  case P1ARA extends KeywordName("P1ARA", "P1ARA")

  /** @group Constructors */
  case P1ADEC extends KeywordName("P1ADEC", "P1ADEC")

  /** @group Constructors */
  case P1ARV extends KeywordName("P1ARV", "P1ARV")

  /** @group Constructors */
  case P1AWAVEL extends KeywordName("P1AWAVEL", "P1AWAVEL")

  /** @group Constructors */
  case P1AEPOCH extends KeywordName("P1AEPOCH", "P1AEPOCH")

  /** @group Constructors */
  case P1AEQUIN extends KeywordName("P1AEQUIN", "P1AEQUIN")

  /** @group Constructors */
  case P1AFRAME extends KeywordName("P1AFRAME", "P1AFRAME")

  /** @group Constructors */
  case P1AOBJEC extends KeywordName("P1AOBJEC", "P1AOBJEC")

  /** @group Constructors */
  case P1APMDEC extends KeywordName("P1APMDEC", "P1APMDEC")

  /** @group Constructors */
  case P1APMRA extends KeywordName("P1APMRA", "P1APMRA")

  /** @group Constructors */
  case P1APARAL extends KeywordName("P1APARAL", "P1APARAL")

  /** @group Constructors */
  case P1FOCUS extends KeywordName("P1FOCUS", "P1FOCUS")

  /** @group Constructors */
  case P2ARA extends KeywordName("P2ARA", "P2ARA")

  /** @group Constructors */
  case P2ADEC extends KeywordName("P2ADEC", "P2ADEC")

  /** @group Constructors */
  case P2ARV extends KeywordName("P2ARV", "P2ARV")

  /** @group Constructors */
  case P2AWAVEL extends KeywordName("P2AWAVEL", "P2AWAVEL")

  /** @group Constructors */
  case P2AEPOCH extends KeywordName("P2AEPOCH", "P2AEPOCH")

  /** @group Constructors */
  case P2AEQUIN extends KeywordName("P2AEQUIN", "P2AEQUIN")

  /** @group Constructors */
  case P2AFRAME extends KeywordName("P2AFRAME", "P2AFRAME")

  /** @group Constructors */
  case P2AOBJEC extends KeywordName("P2AOBJEC", "P2AOBJEC")

  /** @group Constructors */
  case P2APMDEC extends KeywordName("P2APMDEC", "P2APMDEC")

  /** @group Constructors */
  case P2APMRA extends KeywordName("P2APMRA", "P2APMRA")

  /** @group Constructors */
  case P2APARAL extends KeywordName("P2APARAL", "P2APARAL")

  /** @group Constructors */
  case P2FOCUS extends KeywordName("P2FOCUS", "P2FOCUS")

  /** @group Constructors */
  case AOARA extends KeywordName("AOARA", "AOARA")

  /** @group Constructors */
  case AOADEC extends KeywordName("AOADEC", "AOADEC")

  /** @group Constructors */
  case AOARV extends KeywordName("AOARV", "AOARV")

  /** @group Constructors */
  case AOAWAVEL extends KeywordName("AOAWAVEL", "AOAWAVEL")

  /** @group Constructors */
  case AOAEPOCH extends KeywordName("AOAEPOCH", "AOAEPOCH")

  /** @group Constructors */
  case AOAEQUIN extends KeywordName("AOAEQUIN", "AOAEQUIN")

  /** @group Constructors */
  case AOAFRAME extends KeywordName("AOAFRAME", "AOAFRAME")

  /** @group Constructors */
  case AOAOBJEC extends KeywordName("AOAOBJEC", "AOAOBJEC")

  /** @group Constructors */
  case AOAPMDEC extends KeywordName("AOAPMDEC", "AOAPMDEC")

  /** @group Constructors */
  case AOAPMRA extends KeywordName("AOAPMRA", "AOAPMRA")

  /** @group Constructors */
  case AOAPARAL extends KeywordName("AOAPARAL", "AOAPARAL")

  /** @group Constructors */
  case AOFOCUS extends KeywordName("AOFOCUS", "AOFOCUS")

  /** @group Constructors */
  case OIFREQ extends KeywordName("OIFREQ", "OIFREQ")

  /** @group Constructors */
  case P1FREQ extends KeywordName("P1FREQ", "P1FREQ")

  /** @group Constructors */
  case P2FREQ extends KeywordName("P2FREQ", "P2FREQ")

  /** @group Constructors */
  case AIRMASS extends KeywordName("AIRMASS", "AIRMASS")

  /** @group Constructors */
  case AMSTART extends KeywordName("AMSTART", "AMSTART")

  /** @group Constructors */
  case AMEND extends KeywordName("AMEND", "AMEND")

  /** @group Constructors */
  case PROP_MD extends KeywordName("PROP_MD", "PROP_MD")

  /** @group Constructors */
  case RELEASE extends KeywordName("RELEASE", "RELEASE")

  /** @group Constructors */
  case PREIMAGE extends KeywordName("PREIMAGE", "PREIMAGE")

  /** @group Constructors */
  case DATE_OBS extends KeywordName("DATE_OBS", "DATE-OBS")

  /** @group Constructors */
  case TIME_OBS extends KeywordName("TIME_OBS", "TIME-OBS")

  /** @group Constructors */
  case READMODE extends KeywordName("READMODE", "READMODE")

  /** @group Constructors */
  case NREADS extends KeywordName("NREADS", "NREADS")

  /** @group Constructors */
  case GCALLAMP extends KeywordName("GCALLAMP", "GCALLAMP")

  /** @group Constructors */
  case GCALFILT extends KeywordName("GCALFILT", "GCALFILT")

  /** @group Constructors */
  case GCALDIFF extends KeywordName("GCALDIFF", "GCALDIFF")

  /** @group Constructors */
  case GCALSHUT extends KeywordName("GCALSHUT", "GCALSHUT")

  /** @group Constructors */
  case PAR_ANG extends KeywordName("PAR_ANG", "PAR_ANG")

  /** @group Constructors */
  case INPORT extends KeywordName("INPORT", "INPORT")

  /** @group Constructors */
  case ASTROMTC extends KeywordName("ASTROMTC", "ASTROMTC")

  /** @group Constructors */
  case CRFOLLOW extends KeywordName("CRFOLLOW", "CRFOLLOW")

  /** @group Constructors */
  case HUMIDITY extends KeywordName("HUMIDITY", "HUMIDITY")

  /** @group Constructors */
  case TAMBIENT extends KeywordName("TAMBIENT", "TAMBIENT")

  /** @group Constructors */
  case TAMBIEN2 extends KeywordName("TAMBIEN2", "TAMBIEN2")

  /** @group Constructors */
  case PRESSURE extends KeywordName("PRESSURE", "PRESSURE")

  /** @group Constructors */
  case PRESSUR2 extends KeywordName("PRESSUR2", "PRESSUR2")

  /** @group Constructors */
  case DEWPOINT extends KeywordName("DEWPOINT", "DEWPOINT")

  /** @group Constructors */
  case DEWPOIN2 extends KeywordName("DEWPOIN2", "DEWPOIN2")

  /** @group Constructors */
  case WINDSPEE extends KeywordName("WINDSPEE", "WINDSPEE")

  /** @group Constructors */
  case WINDSPE2 extends KeywordName("WINDSPE2", "WINDSPE2")

  /** @group Constructors */
  case WINDDIRE extends KeywordName("WINDDIRE", "WINDDIRE")

  /** @group Constructors */
  case ARRAYID extends KeywordName("ARRAYID", "ARRAYID")

  /** @group Constructors */
  case ARRAYTYP extends KeywordName("ARRAYTYP", "ARRAYTYP")

  /** @group Constructors */
  case UTSTART extends KeywordName("UTSTART", "UTSTART")

  /** @group Constructors */
  case FILTER1 extends KeywordName("FILTER1", "FILTER1")

  /** @group Constructors */
  case FW1_ENG extends KeywordName("FW1_ENG", "FW1_ENG")

  /** @group Constructors */
  case FILTER2 extends KeywordName("FILTER2", "FILTER2")

  /** @group Constructors */
  case FW2_ENG extends KeywordName("FW2_ENG", "FW2_ENG")

  /** @group Constructors */
  case CAMERA extends KeywordName("CAMERA", "CAMERA")

  /** @group Constructors */
  case CAM_ENG extends KeywordName("CAM_ENG", "CAM_ENG")

  /** @group Constructors */
  case SLIT extends KeywordName("SLIT", "SLIT")

  /** @group Constructors */
  case SLIT_ENG extends KeywordName("SLIT_ENG", "SLIT_ENG")

  /** @group Constructors */
  case DECKER extends KeywordName("DECKER", "DECKER")

  /** @group Constructors */
  case DKR_ENG extends KeywordName("DKR_ENG", "DKR_ENG")

  /** @group Constructors */
  case GRATING extends KeywordName("GRATING", "GRATING")

  /** @group Constructors */
  case GR_ENG extends KeywordName("GR_ENG", "GR_ENG")

  /** @group Constructors */
  case GRATWAVE extends KeywordName("GRATWAVE", "GRATWAVE")

  /** @group Constructors */
  case GRATORD extends KeywordName("GRATORD", "GRATORD")

  /** @group Constructors */
  case GRATTILT extends KeywordName("GRATTILT", "GRATTILT")

  /** @group Constructors */
  case PRISM extends KeywordName("PRISM", "PRISM")

  /** @group Constructors */
  case PRSM_ENG extends KeywordName("PRSM_ENG", "PRSM_ENG")

  /** @group Constructors */
  case ACQMIR extends KeywordName("ACQMIR", "ACQMIR")

  /** @group Constructors */
  case COVER extends KeywordName("COVER", "COVER")

  /** @group Constructors */
  case FOCUS extends KeywordName("FOCUS", "FOCUS")

  /** @group Constructors */
  case FCS_ENG extends KeywordName("FCS_ENG", "FCS_ENG")

  /** @group Constructors */
  case DETBIAS extends KeywordName("DETBIAS", "DETBIAS")

  /** @group Constructors */
  case UTEND extends KeywordName("UTEND", "UTEND")

  /** @group Constructors */
  case OBSEPOCH extends KeywordName("OBSEPOCH", "OBSEPOCH")

  /** @group Constructors */
  case GMOSCC extends KeywordName("GMOSCC", "GMOSCC")

  /** @group Constructors */
  case ADCENPST extends KeywordName("ADCENPST", "ADCENPST")

  /** @group Constructors */
  case ADCENPEN extends KeywordName("ADCENPEN", "ADCENPEN")

  /** @group Constructors */
  case ADCENPME extends KeywordName("ADCENPME", "ADCENPME")

  /** @group Constructors */
  case ADCEXPST extends KeywordName("ADCEXPST", "ADCEXPST")

  /** @group Constructors */
  case ADCEXPEN extends KeywordName("ADCEXPEN", "ADCEXPEN")

  /** @group Constructors */
  case ADCEXPME extends KeywordName("ADCEXPME", "ADCEXPME")

  /** @group Constructors */
  case ADCWLEN1 extends KeywordName("ADCWLEN1", "ADCWLEN1")

  /** @group Constructors */
  case ADCWLEN2 extends KeywordName("ADCWLEN2", "ADCWLEN2")

  /** @group Constructors */
  case MASKID extends KeywordName("MASKID", "MASKID")

  /** @group Constructors */
  case MASKNAME extends KeywordName("MASKNAME", "MASKNAME")

  /** @group Constructors */
  case MASKTYP extends KeywordName("MASKTYP", "MASKTYP")

  /** @group Constructors */
  case MASKLOC extends KeywordName("MASKLOC", "MASKLOC")

  /** @group Constructors */
  case FILTID1 extends KeywordName("FILTID1", "FILTID1")

  /** @group Constructors */
  case FILTID2 extends KeywordName("FILTID2", "FILTID2")

  /** @group Constructors */
  case GRATID extends KeywordName("GRATID", "GRATID")

  /** @group Constructors */
  case GRWLEN extends KeywordName("GRWLEN", "GRWLEN")

  /** @group Constructors */
  case CENTWAVE extends KeywordName("CENTWAVE", "CENTWAVE")

  /** @group Constructors */
  case GRORDER extends KeywordName("GRORDER", "GRORDER")

  /** @group Constructors */
  case GRTILT extends KeywordName("GRTILT", "GRTILT")

  /** @group Constructors */
  case GRSTEP extends KeywordName("GRSTEP", "GRSTEP")

  /** @group Constructors */
  case DTAX extends KeywordName("DTAX", "DTAX")

  /** @group Constructors */
  case DTAY extends KeywordName("DTAY", "DTAY")

  /** @group Constructors */
  case DTAZ extends KeywordName("DTAZ", "DTAZ")

  /** @group Constructors */
  case DTAZST extends KeywordName("DTAZST", "DTAZST")

  /** @group Constructors */
  case DTAZEN extends KeywordName("DTAZEN", "DTAZEN")

  /** @group Constructors */
  case DTAZME extends KeywordName("DTAZME", "DTAZME")

  /** @group Constructors */
  case DTMODE extends KeywordName("DTMODE", "DTMODE")

  /** @group Constructors */
  case ADCMODE extends KeywordName("ADCMODE", "ADCMODE")

  /** @group Constructors */
  case GMOSDC extends KeywordName("GMOSDC", "GMOSDC")

  /** @group Constructors */
  case DETTYPE extends KeywordName("DETTYPE", "DETTYPE")

  /** @group Constructors */
  case DETID extends KeywordName("DETID", "DETID")

  /** @group Constructors */
  case EXPOSURE extends KeywordName("EXPOSURE", "EXPOSURE")

  /** @group Constructors */
  case ADCUSED extends KeywordName("ADCUSED", "ADCUSED")

  /** @group Constructors */
  case DETNROI extends KeywordName("DETNROI", "DETNROI")

  /** @group Constructors */
  case DETRO0X extends KeywordName("DETRO0X", "DETRO0X")

  /** @group Constructors */
  case DETRO0XS extends KeywordName("DETRO0XS", "DETRO0XS")

  /** @group Constructors */
  case DETRO0Y extends KeywordName("DETRO0Y", "DETRO0Y")

  /** @group Constructors */
  case DETRO0YS extends KeywordName("DETRO0YS", "DETRO0YS")

  /** @group Constructors */
  case DETRO1X extends KeywordName("DETRO1X", "DETRO1X")

  /** @group Constructors */
  case DETRO1XS extends KeywordName("DETRO1XS", "DETRO1XS")

  /** @group Constructors */
  case DETRO1Y extends KeywordName("DETRO1Y", "DETRO1Y")

  /** @group Constructors */
  case DETRO1YS extends KeywordName("DETRO1YS", "DETRO1YS")

  /** @group Constructors */
  case DETRO2X extends KeywordName("DETRO2X", "DETRO2X")

  /** @group Constructors */
  case DETRO2XS extends KeywordName("DETRO2XS", "DETRO2XS")

  /** @group Constructors */
  case DETRO2Y extends KeywordName("DETRO2Y", "DETRO2Y")

  /** @group Constructors */
  case DETRO2YS extends KeywordName("DETRO2YS", "DETRO2YS")

  /** @group Constructors */
  case DETRO3X extends KeywordName("DETRO3X", "DETRO3X")

  /** @group Constructors */
  case DETRO3XS extends KeywordName("DETRO3XS", "DETRO3XS")

  /** @group Constructors */
  case DETRO3Y extends KeywordName("DETRO3Y", "DETRO3Y")

  /** @group Constructors */
  case DETRO3YS extends KeywordName("DETRO3YS", "DETRO3YS")

  /** @group Constructors */
  case DETRO4X extends KeywordName("DETRO4X", "DETRO4X")

  /** @group Constructors */
  case DETRO4XS extends KeywordName("DETRO4XS", "DETRO4XS")

  /** @group Constructors */
  case DETRO4Y extends KeywordName("DETRO4Y", "DETRO4Y")

  /** @group Constructors */
  case DETRO4YS extends KeywordName("DETRO4YS", "DETRO4YS")

  /** @group Constructors */
  case DETRO5X extends KeywordName("DETRO5X", "DETRO5X")

  /** @group Constructors */
  case DETRO5XS extends KeywordName("DETRO5XS", "DETRO5XS")

  /** @group Constructors */
  case DETRO5Y extends KeywordName("DETRO5Y", "DETRO5Y")

  /** @group Constructors */
  case DETRO5YS extends KeywordName("DETRO5YS", "DETRO5YS")

  /** @group Constructors */
  case DETRO6X extends KeywordName("DETRO6X", "DETRO6X")

  /** @group Constructors */
  case DETRO6XS extends KeywordName("DETRO6XS", "DETRO6XS")

  /** @group Constructors */
  case DETRO6Y extends KeywordName("DETRO6Y", "DETRO6Y")

  /** @group Constructors */
  case DETRO6YS extends KeywordName("DETRO6YS", "DETRO6YS")

  /** @group Constructors */
  case DETRO7X extends KeywordName("DETRO7X", "DETRO7X")

  /** @group Constructors */
  case DETRO7XS extends KeywordName("DETRO7XS", "DETRO7XS")

  /** @group Constructors */
  case DETRO7Y extends KeywordName("DETRO7Y", "DETRO7Y")

  /** @group Constructors */
  case DETRO7YS extends KeywordName("DETRO7YS", "DETRO7YS")

  /** @group Constructors */
  case DETRO8X extends KeywordName("DETRO8X", "DETRO8X")

  /** @group Constructors */
  case DETRO8XS extends KeywordName("DETRO8XS", "DETRO8XS")

  /** @group Constructors */
  case DETRO8Y extends KeywordName("DETRO8Y", "DETRO8Y")

  /** @group Constructors */
  case DETRO8YS extends KeywordName("DETRO8YS", "DETRO8YS")

  /** @group Constructors */
  case DETRO9X extends KeywordName("DETRO9X", "DETRO9X")

  /** @group Constructors */
  case DETRO9XS extends KeywordName("DETRO9XS", "DETRO9XS")

  /** @group Constructors */
  case DETRO9Y extends KeywordName("DETRO9Y", "DETRO9Y")

  /** @group Constructors */
  case DETRO9YS extends KeywordName("DETRO9YS", "DETRO9YS")

  /** @group Constructors */
  case REQTWS01 extends KeywordName("REQTWS01", "REQTWS01")

  /** @group Constructors */
  case REQTWD01 extends KeywordName("REQTWD01", "REQTWD01")

  /** @group Constructors */
  case REQTWN01 extends KeywordName("REQTWN01", "REQTWN01")

  /** @group Constructors */
  case REQTWP01 extends KeywordName("REQTWP01", "REQTWP01")

  /** @group Constructors */
  case REQTWS02 extends KeywordName("REQTWS02", "REQTWS02")

  /** @group Constructors */
  case REQTWD02 extends KeywordName("REQTWD02", "REQTWD02")

  /** @group Constructors */
  case REQTWN02 extends KeywordName("REQTWN02", "REQTWN02")

  /** @group Constructors */
  case REQTWP02 extends KeywordName("REQTWP02", "REQTWP02")

  /** @group Constructors */
  case REQTWS03 extends KeywordName("REQTWS03", "REQTWS03")

  /** @group Constructors */
  case REQTWD03 extends KeywordName("REQTWD03", "REQTWD03")

  /** @group Constructors */
  case REQTWN03 extends KeywordName("REQTWN03", "REQTWN03")

  /** @group Constructors */
  case REQTWP03 extends KeywordName("REQTWP03", "REQTWP03")

  /** @group Constructors */
  case REQTWS04 extends KeywordName("REQTWS04", "REQTWS04")

  /** @group Constructors */
  case REQTWD04 extends KeywordName("REQTWD04", "REQTWD04")

  /** @group Constructors */
  case REQTWN04 extends KeywordName("REQTWN04", "REQTWN04")

  /** @group Constructors */
  case REQTWP04 extends KeywordName("REQTWP04", "REQTWP04")

  /** @group Constructors */
  case REQTWS05 extends KeywordName("REQTWS05", "REQTWS05")

  /** @group Constructors */
  case REQTWD05 extends KeywordName("REQTWD05", "REQTWD05")

  /** @group Constructors */
  case REQTWN05 extends KeywordName("REQTWN05", "REQTWN05")

  /** @group Constructors */
  case REQTWP05 extends KeywordName("REQTWP05", "REQTWP05")

  /** @group Constructors */
  case REQTWS06 extends KeywordName("REQTWS06", "REQTWS06")

  /** @group Constructors */
  case REQTWD06 extends KeywordName("REQTWD06", "REQTWD06")

  /** @group Constructors */
  case REQTWN06 extends KeywordName("REQTWN06", "REQTWN06")

  /** @group Constructors */
  case REQTWP06 extends KeywordName("REQTWP06", "REQTWP06")

  /** @group Constructors */
  case REQTWS07 extends KeywordName("REQTWS07", "REQTWS07")

  /** @group Constructors */
  case REQTWD07 extends KeywordName("REQTWD07", "REQTWD07")

  /** @group Constructors */
  case REQTWN07 extends KeywordName("REQTWN07", "REQTWN07")

  /** @group Constructors */
  case REQTWP07 extends KeywordName("REQTWP07", "REQTWP07")

  /** @group Constructors */
  case REQTWS08 extends KeywordName("REQTWS08", "REQTWS08")

  /** @group Constructors */
  case REQTWD08 extends KeywordName("REQTWD08", "REQTWD08")

  /** @group Constructors */
  case REQTWN08 extends KeywordName("REQTWN08", "REQTWN08")

  /** @group Constructors */
  case REQTWP08 extends KeywordName("REQTWP08", "REQTWP08")

  /** @group Constructors */
  case REQTWS09 extends KeywordName("REQTWS09", "REQTWS09")

  /** @group Constructors */
  case REQTWD09 extends KeywordName("REQTWD09", "REQTWD09")

  /** @group Constructors */
  case REQTWN09 extends KeywordName("REQTWN09", "REQTWN09")

  /** @group Constructors */
  case REQTWP09 extends KeywordName("REQTWP09", "REQTWP09")

  /** @group Constructors */
  case REQTWS10 extends KeywordName("REQTWS10", "REQTWS10")

  /** @group Constructors */
  case REQTWD10 extends KeywordName("REQTWD10", "REQTWD10")

  /** @group Constructors */
  case REQTWN10 extends KeywordName("REQTWN10", "REQTWN10")

  /** @group Constructors */
  case REQTWP10 extends KeywordName("REQTWP10", "REQTWP10")

  /** @group Constructors */
  case REQTWS11 extends KeywordName("REQTWS11", "REQTWS11")

  /** @group Constructors */
  case REQTWD11 extends KeywordName("REQTWD11", "REQTWD11")

  /** @group Constructors */
  case REQTWN11 extends KeywordName("REQTWN11", "REQTWN11")

  /** @group Constructors */
  case REQTWP11 extends KeywordName("REQTWP11", "REQTWP11")

  /** @group Constructors */
  case REQTWS12 extends KeywordName("REQTWS12", "REQTWS12")

  /** @group Constructors */
  case REQTWD12 extends KeywordName("REQTWD12", "REQTWD12")

  /** @group Constructors */
  case REQTWN12 extends KeywordName("REQTWN12", "REQTWN12")

  /** @group Constructors */
  case REQTWP12 extends KeywordName("REQTWP12", "REQTWP12")

  /** @group Constructors */
  case REQTWS13 extends KeywordName("REQTWS13", "REQTWS13")

  /** @group Constructors */
  case REQTWD13 extends KeywordName("REQTWD13", "REQTWD13")

  /** @group Constructors */
  case REQTWN13 extends KeywordName("REQTWN13", "REQTWN13")

  /** @group Constructors */
  case REQTWP13 extends KeywordName("REQTWP13", "REQTWP13")

  /** @group Constructors */
  case REQTWS14 extends KeywordName("REQTWS14", "REQTWS14")

  /** @group Constructors */
  case REQTWD14 extends KeywordName("REQTWD14", "REQTWD14")

  /** @group Constructors */
  case REQTWN14 extends KeywordName("REQTWN14", "REQTWN14")

  /** @group Constructors */
  case REQTWP14 extends KeywordName("REQTWP14", "REQTWP14")

  /** @group Constructors */
  case REQTWS15 extends KeywordName("REQTWS15", "REQTWS15")

  /** @group Constructors */
  case REQTWD15 extends KeywordName("REQTWD15", "REQTWD15")

  /** @group Constructors */
  case REQTWN15 extends KeywordName("REQTWN15", "REQTWN15")

  /** @group Constructors */
  case REQTWP15 extends KeywordName("REQTWP15", "REQTWP15")

  /** @group Constructors */
  case REQTWS16 extends KeywordName("REQTWS16", "REQTWS16")

  /** @group Constructors */
  case REQTWD16 extends KeywordName("REQTWD16", "REQTWD16")

  /** @group Constructors */
  case REQTWN16 extends KeywordName("REQTWN16", "REQTWN16")

  /** @group Constructors */
  case REQTWP16 extends KeywordName("REQTWP16", "REQTWP16")

  /** @group Constructors */
  case REQTWS17 extends KeywordName("REQTWS17", "REQTWS17")

  /** @group Constructors */
  case REQTWD17 extends KeywordName("REQTWD17", "REQTWD17")

  /** @group Constructors */
  case REQTWN17 extends KeywordName("REQTWN17", "REQTWN17")

  /** @group Constructors */
  case REQTWP17 extends KeywordName("REQTWP17", "REQTWP17")

  /** @group Constructors */
  case REQTWS18 extends KeywordName("REQTWS18", "REQTWS18")

  /** @group Constructors */
  case REQTWD18 extends KeywordName("REQTWD18", "REQTWD18")

  /** @group Constructors */
  case REQTWN18 extends KeywordName("REQTWN18", "REQTWN18")

  /** @group Constructors */
  case REQTWP18 extends KeywordName("REQTWP18", "REQTWP18")

  /** @group Constructors */
  case REQTWS19 extends KeywordName("REQTWS19", "REQTWS19")

  /** @group Constructors */
  case REQTWD19 extends KeywordName("REQTWD19", "REQTWD19")

  /** @group Constructors */
  case REQTWN19 extends KeywordName("REQTWN19", "REQTWN19")

  /** @group Constructors */
  case REQTWP19 extends KeywordName("REQTWP19", "REQTWP19")

  /** @group Constructors */
  case REQTWS20 extends KeywordName("REQTWS20", "REQTWS20")

  /** @group Constructors */
  case REQTWD20 extends KeywordName("REQTWD20", "REQTWD20")

  /** @group Constructors */
  case REQTWN20 extends KeywordName("REQTWN20", "REQTWN20")

  /** @group Constructors */
  case REQTWP20 extends KeywordName("REQTWP20", "REQTWP20")

  /** @group Constructors */
  case REQTWS21 extends KeywordName("REQTWS21", "REQTWS21")

  /** @group Constructors */
  case REQTWD21 extends KeywordName("REQTWD21", "REQTWD21")

  /** @group Constructors */
  case REQTWN21 extends KeywordName("REQTWN21", "REQTWN21")

  /** @group Constructors */
  case REQTWP21 extends KeywordName("REQTWP21", "REQTWP21")

  /** @group Constructors */
  case REQTWS22 extends KeywordName("REQTWS22", "REQTWS22")

  /** @group Constructors */
  case REQTWD22 extends KeywordName("REQTWD22", "REQTWD22")

  /** @group Constructors */
  case REQTWN22 extends KeywordName("REQTWN22", "REQTWN22")

  /** @group Constructors */
  case REQTWP22 extends KeywordName("REQTWP22", "REQTWP22")

  /** @group Constructors */
  case REQTWS23 extends KeywordName("REQTWS23", "REQTWS23")

  /** @group Constructors */
  case REQTWD23 extends KeywordName("REQTWD23", "REQTWD23")

  /** @group Constructors */
  case REQTWN23 extends KeywordName("REQTWN23", "REQTWN23")

  /** @group Constructors */
  case REQTWP23 extends KeywordName("REQTWP23", "REQTWP23")

  /** @group Constructors */
  case REQTWS24 extends KeywordName("REQTWS24", "REQTWS24")

  /** @group Constructors */
  case REQTWD24 extends KeywordName("REQTWD24", "REQTWD24")

  /** @group Constructors */
  case REQTWN24 extends KeywordName("REQTWN24", "REQTWN24")

  /** @group Constructors */
  case REQTWP24 extends KeywordName("REQTWP24", "REQTWP24")

  /** @group Constructors */
  case REQTWS25 extends KeywordName("REQTWS25", "REQTWS25")

  /** @group Constructors */
  case REQTWD25 extends KeywordName("REQTWD25", "REQTWD25")

  /** @group Constructors */
  case REQTWN25 extends KeywordName("REQTWN25", "REQTWN25")

  /** @group Constructors */
  case REQTWP25 extends KeywordName("REQTWP25", "REQTWP25")

  /** @group Constructors */
  case REQTWS26 extends KeywordName("REQTWS26", "REQTWS26")

  /** @group Constructors */
  case REQTWD26 extends KeywordName("REQTWD26", "REQTWD26")

  /** @group Constructors */
  case REQTWN26 extends KeywordName("REQTWN26", "REQTWN26")

  /** @group Constructors */
  case REQTWP26 extends KeywordName("REQTWP26", "REQTWP26")

  /** @group Constructors */
  case REQTWS27 extends KeywordName("REQTWS27", "REQTWS27")

  /** @group Constructors */
  case REQTWD27 extends KeywordName("REQTWD27", "REQTWD27")

  /** @group Constructors */
  case REQTWN27 extends KeywordName("REQTWN27", "REQTWN27")

  /** @group Constructors */
  case REQTWP27 extends KeywordName("REQTWP27", "REQTWP27")

  /** @group Constructors */
  case REQTWS28 extends KeywordName("REQTWS28", "REQTWS28")

  /** @group Constructors */
  case REQTWD28 extends KeywordName("REQTWD28", "REQTWD28")

  /** @group Constructors */
  case REQTWN28 extends KeywordName("REQTWN28", "REQTWN28")

  /** @group Constructors */
  case REQTWP28 extends KeywordName("REQTWP28", "REQTWP28")

  /** @group Constructors */
  case REQTWS29 extends KeywordName("REQTWS29", "REQTWS29")

  /** @group Constructors */
  case REQTWD29 extends KeywordName("REQTWD29", "REQTWD29")

  /** @group Constructors */
  case REQTWN29 extends KeywordName("REQTWN29", "REQTWN29")

  /** @group Constructors */
  case REQTWP29 extends KeywordName("REQTWP29", "REQTWP29")

  /** @group Constructors */
  case REQTWS30 extends KeywordName("REQTWS30", "REQTWS30")

  /** @group Constructors */
  case REQTWD30 extends KeywordName("REQTWD30", "REQTWD30")

  /** @group Constructors */
  case REQTWN30 extends KeywordName("REQTWN30", "REQTWN30")

  /** @group Constructors */
  case REQTWP30 extends KeywordName("REQTWP30", "REQTWP30")

  /** @group Constructors */
  case REQTWS31 extends KeywordName("REQTWS31", "REQTWS31")

  /** @group Constructors */
  case REQTWD31 extends KeywordName("REQTWD31", "REQTWD31")

  /** @group Constructors */
  case REQTWN31 extends KeywordName("REQTWN31", "REQTWN31")

  /** @group Constructors */
  case REQTWP31 extends KeywordName("REQTWP31", "REQTWP31")

  /** @group Constructors */
  case REQTWS32 extends KeywordName("REQTWS32", "REQTWS32")

  /** @group Constructors */
  case REQTWD32 extends KeywordName("REQTWD32", "REQTWD32")

  /** @group Constructors */
  case REQTWN32 extends KeywordName("REQTWN32", "REQTWN32")

  /** @group Constructors */
  case REQTWP32 extends KeywordName("REQTWP32", "REQTWP32")

  /** @group Constructors */
  case REQTWS33 extends KeywordName("REQTWS33", "REQTWS33")

  /** @group Constructors */
  case REQTWD33 extends KeywordName("REQTWD33", "REQTWD33")

  /** @group Constructors */
  case REQTWN33 extends KeywordName("REQTWN33", "REQTWN33")

  /** @group Constructors */
  case REQTWP33 extends KeywordName("REQTWP33", "REQTWP33")

  /** @group Constructors */
  case REQTWS34 extends KeywordName("REQTWS34", "REQTWS34")

  /** @group Constructors */
  case REQTWD34 extends KeywordName("REQTWD34", "REQTWD34")

  /** @group Constructors */
  case REQTWN34 extends KeywordName("REQTWN34", "REQTWN34")

  /** @group Constructors */
  case REQTWP34 extends KeywordName("REQTWP34", "REQTWP34")

  /** @group Constructors */
  case REQTWS35 extends KeywordName("REQTWS35", "REQTWS35")

  /** @group Constructors */
  case REQTWD35 extends KeywordName("REQTWD35", "REQTWD35")

  /** @group Constructors */
  case REQTWN35 extends KeywordName("REQTWN35", "REQTWN35")

  /** @group Constructors */
  case REQTWP35 extends KeywordName("REQTWP35", "REQTWP35")

  /** @group Constructors */
  case REQTWS36 extends KeywordName("REQTWS36", "REQTWS36")

  /** @group Constructors */
  case REQTWD36 extends KeywordName("REQTWD36", "REQTWD36")

  /** @group Constructors */
  case REQTWN36 extends KeywordName("REQTWN36", "REQTWN36")

  /** @group Constructors */
  case REQTWP36 extends KeywordName("REQTWP36", "REQTWP36")

  /** @group Constructors */
  case REQTWS37 extends KeywordName("REQTWS37", "REQTWS37")

  /** @group Constructors */
  case REQTWD37 extends KeywordName("REQTWD37", "REQTWD37")

  /** @group Constructors */
  case REQTWN37 extends KeywordName("REQTWN37", "REQTWN37")

  /** @group Constructors */
  case REQTWP37 extends KeywordName("REQTWP37", "REQTWP37")

  /** @group Constructors */
  case REQTWS38 extends KeywordName("REQTWS38", "REQTWS38")

  /** @group Constructors */
  case REQTWD38 extends KeywordName("REQTWD38", "REQTWD38")

  /** @group Constructors */
  case REQTWN38 extends KeywordName("REQTWN38", "REQTWN38")

  /** @group Constructors */
  case REQTWP38 extends KeywordName("REQTWP38", "REQTWP38")

  /** @group Constructors */
  case REQTWS39 extends KeywordName("REQTWS39", "REQTWS39")

  /** @group Constructors */
  case REQTWD39 extends KeywordName("REQTWD39", "REQTWD39")

  /** @group Constructors */
  case REQTWN39 extends KeywordName("REQTWN39", "REQTWN39")

  /** @group Constructors */
  case REQTWP39 extends KeywordName("REQTWP39", "REQTWP39")

  /** @group Constructors */
  case REQTWS40 extends KeywordName("REQTWS40", "REQTWS40")

  /** @group Constructors */
  case REQTWD40 extends KeywordName("REQTWD40", "REQTWD40")

  /** @group Constructors */
  case REQTWN40 extends KeywordName("REQTWN40", "REQTWN40")

  /** @group Constructors */
  case REQTWP40 extends KeywordName("REQTWP40", "REQTWP40")

  /** @group Constructors */
  case REQTWS41 extends KeywordName("REQTWS41", "REQTWS41")

  /** @group Constructors */
  case REQTWD41 extends KeywordName("REQTWD41", "REQTWD41")

  /** @group Constructors */
  case REQTWN41 extends KeywordName("REQTWN41", "REQTWN41")

  /** @group Constructors */
  case REQTWP41 extends KeywordName("REQTWP41", "REQTWP41")

  /** @group Constructors */
  case REQTWS42 extends KeywordName("REQTWS42", "REQTWS42")

  /** @group Constructors */
  case REQTWD42 extends KeywordName("REQTWD42", "REQTWD42")

  /** @group Constructors */
  case REQTWN42 extends KeywordName("REQTWN42", "REQTWN42")

  /** @group Constructors */
  case REQTWP42 extends KeywordName("REQTWP42", "REQTWP42")

  /** @group Constructors */
  case REQTWS43 extends KeywordName("REQTWS43", "REQTWS43")

  /** @group Constructors */
  case REQTWD43 extends KeywordName("REQTWD43", "REQTWD43")

  /** @group Constructors */
  case REQTWN43 extends KeywordName("REQTWN43", "REQTWN43")

  /** @group Constructors */
  case REQTWP43 extends KeywordName("REQTWP43", "REQTWP43")

  /** @group Constructors */
  case REQTWS44 extends KeywordName("REQTWS44", "REQTWS44")

  /** @group Constructors */
  case REQTWD44 extends KeywordName("REQTWD44", "REQTWD44")

  /** @group Constructors */
  case REQTWN44 extends KeywordName("REQTWN44", "REQTWN44")

  /** @group Constructors */
  case REQTWP44 extends KeywordName("REQTWP44", "REQTWP44")

  /** @group Constructors */
  case REQTWS45 extends KeywordName("REQTWS45", "REQTWS45")

  /** @group Constructors */
  case REQTWD45 extends KeywordName("REQTWD45", "REQTWD45")

  /** @group Constructors */
  case REQTWN45 extends KeywordName("REQTWN45", "REQTWN45")

  /** @group Constructors */
  case REQTWP45 extends KeywordName("REQTWP45", "REQTWP45")

  /** @group Constructors */
  case REQTWS46 extends KeywordName("REQTWS46", "REQTWS46")

  /** @group Constructors */
  case REQTWD46 extends KeywordName("REQTWD46", "REQTWD46")

  /** @group Constructors */
  case REQTWN46 extends KeywordName("REQTWN46", "REQTWN46")

  /** @group Constructors */
  case REQTWP46 extends KeywordName("REQTWP46", "REQTWP46")

  /** @group Constructors */
  case REQTWS47 extends KeywordName("REQTWS47", "REQTWS47")

  /** @group Constructors */
  case REQTWD47 extends KeywordName("REQTWD47", "REQTWD47")

  /** @group Constructors */
  case REQTWN47 extends KeywordName("REQTWN47", "REQTWN47")

  /** @group Constructors */
  case REQTWP47 extends KeywordName("REQTWP47", "REQTWP47")

  /** @group Constructors */
  case REQTWS48 extends KeywordName("REQTWS48", "REQTWS48")

  /** @group Constructors */
  case REQTWD48 extends KeywordName("REQTWD48", "REQTWD48")

  /** @group Constructors */
  case REQTWN48 extends KeywordName("REQTWN48", "REQTWN48")

  /** @group Constructors */
  case REQTWP48 extends KeywordName("REQTWP48", "REQTWP48")

  /** @group Constructors */
  case REQTWS49 extends KeywordName("REQTWS49", "REQTWS49")

  /** @group Constructors */
  case REQTWD49 extends KeywordName("REQTWD49", "REQTWD49")

  /** @group Constructors */
  case REQTWN49 extends KeywordName("REQTWN49", "REQTWN49")

  /** @group Constructors */
  case REQTWP49 extends KeywordName("REQTWP49", "REQTWP49")

  /** @group Constructors */
  case REQTWS50 extends KeywordName("REQTWS50", "REQTWS50")

  /** @group Constructors */
  case REQTWD50 extends KeywordName("REQTWD50", "REQTWD50")

  /** @group Constructors */
  case REQTWN50 extends KeywordName("REQTWN50", "REQTWN50")

  /** @group Constructors */
  case REQTWP50 extends KeywordName("REQTWP50", "REQTWP50")

  /** @group Constructors */
  case REQTWS51 extends KeywordName("REQTWS51", "REQTWS51")

  /** @group Constructors */
  case REQTWD51 extends KeywordName("REQTWD51", "REQTWD51")

  /** @group Constructors */
  case REQTWN51 extends KeywordName("REQTWN51", "REQTWN51")

  /** @group Constructors */
  case REQTWP51 extends KeywordName("REQTWP51", "REQTWP51")

  /** @group Constructors */
  case REQTWS52 extends KeywordName("REQTWS52", "REQTWS52")

  /** @group Constructors */
  case REQTWD52 extends KeywordName("REQTWD52", "REQTWD52")

  /** @group Constructors */
  case REQTWN52 extends KeywordName("REQTWN52", "REQTWN52")

  /** @group Constructors */
  case REQTWP52 extends KeywordName("REQTWP52", "REQTWP52")

  /** @group Constructors */
  case REQTWS53 extends KeywordName("REQTWS53", "REQTWS53")

  /** @group Constructors */
  case REQTWD53 extends KeywordName("REQTWD53", "REQTWD53")

  /** @group Constructors */
  case REQTWN53 extends KeywordName("REQTWN53", "REQTWN53")

  /** @group Constructors */
  case REQTWP53 extends KeywordName("REQTWP53", "REQTWP53")

  /** @group Constructors */
  case REQTWS54 extends KeywordName("REQTWS54", "REQTWS54")

  /** @group Constructors */
  case REQTWD54 extends KeywordName("REQTWD54", "REQTWD54")

  /** @group Constructors */
  case REQTWN54 extends KeywordName("REQTWN54", "REQTWN54")

  /** @group Constructors */
  case REQTWP54 extends KeywordName("REQTWP54", "REQTWP54")

  /** @group Constructors */
  case REQTWS55 extends KeywordName("REQTWS55", "REQTWS55")

  /** @group Constructors */
  case REQTWD55 extends KeywordName("REQTWD55", "REQTWD55")

  /** @group Constructors */
  case REQTWN55 extends KeywordName("REQTWN55", "REQTWN55")

  /** @group Constructors */
  case REQTWP55 extends KeywordName("REQTWP55", "REQTWP55")

  /** @group Constructors */
  case REQTWS56 extends KeywordName("REQTWS56", "REQTWS56")

  /** @group Constructors */
  case REQTWD56 extends KeywordName("REQTWD56", "REQTWD56")

  /** @group Constructors */
  case REQTWN56 extends KeywordName("REQTWN56", "REQTWN56")

  /** @group Constructors */
  case REQTWP56 extends KeywordName("REQTWP56", "REQTWP56")

  /** @group Constructors */
  case REQTWS57 extends KeywordName("REQTWS57", "REQTWS57")

  /** @group Constructors */
  case REQTWD57 extends KeywordName("REQTWD57", "REQTWD57")

  /** @group Constructors */
  case REQTWN57 extends KeywordName("REQTWN57", "REQTWN57")

  /** @group Constructors */
  case REQTWP57 extends KeywordName("REQTWP57", "REQTWP57")

  /** @group Constructors */
  case REQTWS58 extends KeywordName("REQTWS58", "REQTWS58")

  /** @group Constructors */
  case REQTWD58 extends KeywordName("REQTWD58", "REQTWD58")

  /** @group Constructors */
  case REQTWN58 extends KeywordName("REQTWN58", "REQTWN58")

  /** @group Constructors */
  case REQTWP58 extends KeywordName("REQTWP58", "REQTWP58")

  /** @group Constructors */
  case REQTWS59 extends KeywordName("REQTWS59", "REQTWS59")

  /** @group Constructors */
  case REQTWD59 extends KeywordName("REQTWD59", "REQTWD59")

  /** @group Constructors */
  case REQTWN59 extends KeywordName("REQTWN59", "REQTWN59")

  /** @group Constructors */
  case REQTWP59 extends KeywordName("REQTWP59", "REQTWP59")

  /** @group Constructors */
  case REQTWS60 extends KeywordName("REQTWS60", "REQTWS60")

  /** @group Constructors */
  case REQTWD60 extends KeywordName("REQTWD60", "REQTWD60")

  /** @group Constructors */
  case REQTWN60 extends KeywordName("REQTWN60", "REQTWN60")

  /** @group Constructors */
  case REQTWP60 extends KeywordName("REQTWP60", "REQTWP60")

  /** @group Constructors */
  case REQTWS61 extends KeywordName("REQTWS61", "REQTWS61")

  /** @group Constructors */
  case REQTWD61 extends KeywordName("REQTWD61", "REQTWD61")

  /** @group Constructors */
  case REQTWN61 extends KeywordName("REQTWN61", "REQTWN61")

  /** @group Constructors */
  case REQTWP61 extends KeywordName("REQTWP61", "REQTWP61")

  /** @group Constructors */
  case REQTWS62 extends KeywordName("REQTWS62", "REQTWS62")

  /** @group Constructors */
  case REQTWD62 extends KeywordName("REQTWD62", "REQTWD62")

  /** @group Constructors */
  case REQTWN62 extends KeywordName("REQTWN62", "REQTWN62")

  /** @group Constructors */
  case REQTWP62 extends KeywordName("REQTWP62", "REQTWP62")

  /** @group Constructors */
  case REQTWS63 extends KeywordName("REQTWS63", "REQTWS63")

  /** @group Constructors */
  case REQTWD63 extends KeywordName("REQTWD63", "REQTWD63")

  /** @group Constructors */
  case REQTWN63 extends KeywordName("REQTWN63", "REQTWN63")

  /** @group Constructors */
  case REQTWP63 extends KeywordName("REQTWP63", "REQTWP63")

  /** @group Constructors */
  case REQTWS64 extends KeywordName("REQTWS64", "REQTWS64")

  /** @group Constructors */
  case REQTWD64 extends KeywordName("REQTWD64", "REQTWD64")

  /** @group Constructors */
  case REQTWN64 extends KeywordName("REQTWN64", "REQTWN64")

  /** @group Constructors */
  case REQTWP64 extends KeywordName("REQTWP64", "REQTWP64")

  /** @group Constructors */
  case REQTWS65 extends KeywordName("REQTWS65", "REQTWS65")

  /** @group Constructors */
  case REQTWD65 extends KeywordName("REQTWD65", "REQTWD65")

  /** @group Constructors */
  case REQTWN65 extends KeywordName("REQTWN65", "REQTWN65")

  /** @group Constructors */
  case REQTWP65 extends KeywordName("REQTWP65", "REQTWP65")

  /** @group Constructors */
  case REQTWS66 extends KeywordName("REQTWS66", "REQTWS66")

  /** @group Constructors */
  case REQTWD66 extends KeywordName("REQTWD66", "REQTWD66")

  /** @group Constructors */
  case REQTWN66 extends KeywordName("REQTWN66", "REQTWN66")

  /** @group Constructors */
  case REQTWP66 extends KeywordName("REQTWP66", "REQTWP66")

  /** @group Constructors */
  case REQTWS67 extends KeywordName("REQTWS67", "REQTWS67")

  /** @group Constructors */
  case REQTWD67 extends KeywordName("REQTWD67", "REQTWD67")

  /** @group Constructors */
  case REQTWN67 extends KeywordName("REQTWN67", "REQTWN67")

  /** @group Constructors */
  case REQTWP67 extends KeywordName("REQTWP67", "REQTWP67")

  /** @group Constructors */
  case REQTWS68 extends KeywordName("REQTWS68", "REQTWS68")

  /** @group Constructors */
  case REQTWD68 extends KeywordName("REQTWD68", "REQTWD68")

  /** @group Constructors */
  case REQTWN68 extends KeywordName("REQTWN68", "REQTWN68")

  /** @group Constructors */
  case REQTWP68 extends KeywordName("REQTWP68", "REQTWP68")

  /** @group Constructors */
  case REQTWS69 extends KeywordName("REQTWS69", "REQTWS69")

  /** @group Constructors */
  case REQTWD69 extends KeywordName("REQTWD69", "REQTWD69")

  /** @group Constructors */
  case REQTWN69 extends KeywordName("REQTWN69", "REQTWN69")

  /** @group Constructors */
  case REQTWP69 extends KeywordName("REQTWP69", "REQTWP69")

  /** @group Constructors */
  case REQTWS70 extends KeywordName("REQTWS70", "REQTWS70")

  /** @group Constructors */
  case REQTWD70 extends KeywordName("REQTWD70", "REQTWD70")

  /** @group Constructors */
  case REQTWN70 extends KeywordName("REQTWN70", "REQTWN70")

  /** @group Constructors */
  case REQTWP70 extends KeywordName("REQTWP70", "REQTWP70")

  /** @group Constructors */
  case REQTWS71 extends KeywordName("REQTWS71", "REQTWS71")

  /** @group Constructors */
  case REQTWD71 extends KeywordName("REQTWD71", "REQTWD71")

  /** @group Constructors */
  case REQTWN71 extends KeywordName("REQTWN71", "REQTWN71")

  /** @group Constructors */
  case REQTWP71 extends KeywordName("REQTWP71", "REQTWP71")

  /** @group Constructors */
  case REQTWS72 extends KeywordName("REQTWS72", "REQTWS72")

  /** @group Constructors */
  case REQTWD72 extends KeywordName("REQTWD72", "REQTWD72")

  /** @group Constructors */
  case REQTWN72 extends KeywordName("REQTWN72", "REQTWN72")

  /** @group Constructors */
  case REQTWP72 extends KeywordName("REQTWP72", "REQTWP72")

  /** @group Constructors */
  case REQTWS73 extends KeywordName("REQTWS73", "REQTWS73")

  /** @group Constructors */
  case REQTWD73 extends KeywordName("REQTWD73", "REQTWD73")

  /** @group Constructors */
  case REQTWN73 extends KeywordName("REQTWN73", "REQTWN73")

  /** @group Constructors */
  case REQTWP73 extends KeywordName("REQTWP73", "REQTWP73")

  /** @group Constructors */
  case REQTWS74 extends KeywordName("REQTWS74", "REQTWS74")

  /** @group Constructors */
  case REQTWD74 extends KeywordName("REQTWD74", "REQTWD74")

  /** @group Constructors */
  case REQTWN74 extends KeywordName("REQTWN74", "REQTWN74")

  /** @group Constructors */
  case REQTWP74 extends KeywordName("REQTWP74", "REQTWP74")

  /** @group Constructors */
  case REQTWS75 extends KeywordName("REQTWS75", "REQTWS75")

  /** @group Constructors */
  case REQTWD75 extends KeywordName("REQTWD75", "REQTWD75")

  /** @group Constructors */
  case REQTWN75 extends KeywordName("REQTWN75", "REQTWN75")

  /** @group Constructors */
  case REQTWP75 extends KeywordName("REQTWP75", "REQTWP75")

  /** @group Constructors */
  case REQTWS76 extends KeywordName("REQTWS76", "REQTWS76")

  /** @group Constructors */
  case REQTWD76 extends KeywordName("REQTWD76", "REQTWD76")

  /** @group Constructors */
  case REQTWN76 extends KeywordName("REQTWN76", "REQTWN76")

  /** @group Constructors */
  case REQTWP76 extends KeywordName("REQTWP76", "REQTWP76")

  /** @group Constructors */
  case REQTWS77 extends KeywordName("REQTWS77", "REQTWS77")

  /** @group Constructors */
  case REQTWD77 extends KeywordName("REQTWD77", "REQTWD77")

  /** @group Constructors */
  case REQTWN77 extends KeywordName("REQTWN77", "REQTWN77")

  /** @group Constructors */
  case REQTWP77 extends KeywordName("REQTWP77", "REQTWP77")

  /** @group Constructors */
  case REQTWS78 extends KeywordName("REQTWS78", "REQTWS78")

  /** @group Constructors */
  case REQTWD78 extends KeywordName("REQTWD78", "REQTWD78")

  /** @group Constructors */
  case REQTWN78 extends KeywordName("REQTWN78", "REQTWN78")

  /** @group Constructors */
  case REQTWP78 extends KeywordName("REQTWP78", "REQTWP78")

  /** @group Constructors */
  case REQTWS79 extends KeywordName("REQTWS79", "REQTWS79")

  /** @group Constructors */
  case REQTWD79 extends KeywordName("REQTWD79", "REQTWD79")

  /** @group Constructors */
  case REQTWN79 extends KeywordName("REQTWN79", "REQTWN79")

  /** @group Constructors */
  case REQTWP79 extends KeywordName("REQTWP79", "REQTWP79")

  /** @group Constructors */
  case REQTWS80 extends KeywordName("REQTWS80", "REQTWS80")

  /** @group Constructors */
  case REQTWD80 extends KeywordName("REQTWD80", "REQTWD80")

  /** @group Constructors */
  case REQTWN80 extends KeywordName("REQTWN80", "REQTWN80")

  /** @group Constructors */
  case REQTWP80 extends KeywordName("REQTWP80", "REQTWP80")

  /** @group Constructors */
  case REQTWS81 extends KeywordName("REQTWS81", "REQTWS81")

  /** @group Constructors */
  case REQTWD81 extends KeywordName("REQTWD81", "REQTWD81")

  /** @group Constructors */
  case REQTWN81 extends KeywordName("REQTWN81", "REQTWN81")

  /** @group Constructors */
  case REQTWP81 extends KeywordName("REQTWP81", "REQTWP81")

  /** @group Constructors */
  case REQTWS82 extends KeywordName("REQTWS82", "REQTWS82")

  /** @group Constructors */
  case REQTWD82 extends KeywordName("REQTWD82", "REQTWD82")

  /** @group Constructors */
  case REQTWN82 extends KeywordName("REQTWN82", "REQTWN82")

  /** @group Constructors */
  case REQTWP82 extends KeywordName("REQTWP82", "REQTWP82")

  /** @group Constructors */
  case REQTWS83 extends KeywordName("REQTWS83", "REQTWS83")

  /** @group Constructors */
  case REQTWD83 extends KeywordName("REQTWD83", "REQTWD83")

  /** @group Constructors */
  case REQTWN83 extends KeywordName("REQTWN83", "REQTWN83")

  /** @group Constructors */
  case REQTWP83 extends KeywordName("REQTWP83", "REQTWP83")

  /** @group Constructors */
  case REQTWS84 extends KeywordName("REQTWS84", "REQTWS84")

  /** @group Constructors */
  case REQTWD84 extends KeywordName("REQTWD84", "REQTWD84")

  /** @group Constructors */
  case REQTWN84 extends KeywordName("REQTWN84", "REQTWN84")

  /** @group Constructors */
  case REQTWP84 extends KeywordName("REQTWP84", "REQTWP84")

  /** @group Constructors */
  case REQTWS85 extends KeywordName("REQTWS85", "REQTWS85")

  /** @group Constructors */
  case REQTWD85 extends KeywordName("REQTWD85", "REQTWD85")

  /** @group Constructors */
  case REQTWN85 extends KeywordName("REQTWN85", "REQTWN85")

  /** @group Constructors */
  case REQTWP85 extends KeywordName("REQTWP85", "REQTWP85")

  /** @group Constructors */
  case REQTWS86 extends KeywordName("REQTWS86", "REQTWS86")

  /** @group Constructors */
  case REQTWD86 extends KeywordName("REQTWD86", "REQTWD86")

  /** @group Constructors */
  case REQTWN86 extends KeywordName("REQTWN86", "REQTWN86")

  /** @group Constructors */
  case REQTWP86 extends KeywordName("REQTWP86", "REQTWP86")

  /** @group Constructors */
  case REQTWS87 extends KeywordName("REQTWS87", "REQTWS87")

  /** @group Constructors */
  case REQTWD87 extends KeywordName("REQTWD87", "REQTWD87")

  /** @group Constructors */
  case REQTWN87 extends KeywordName("REQTWN87", "REQTWN87")

  /** @group Constructors */
  case REQTWP87 extends KeywordName("REQTWP87", "REQTWP87")

  /** @group Constructors */
  case REQTWS88 extends KeywordName("REQTWS88", "REQTWS88")

  /** @group Constructors */
  case REQTWD88 extends KeywordName("REQTWD88", "REQTWD88")

  /** @group Constructors */
  case REQTWN88 extends KeywordName("REQTWN88", "REQTWN88")

  /** @group Constructors */
  case REQTWP88 extends KeywordName("REQTWP88", "REQTWP88")

  /** @group Constructors */
  case REQTWS89 extends KeywordName("REQTWS89", "REQTWS89")

  /** @group Constructors */
  case REQTWD89 extends KeywordName("REQTWD89", "REQTWD89")

  /** @group Constructors */
  case REQTWN89 extends KeywordName("REQTWN89", "REQTWN89")

  /** @group Constructors */
  case REQTWP89 extends KeywordName("REQTWP89", "REQTWP89")

  /** @group Constructors */
  case REQTWS90 extends KeywordName("REQTWS90", "REQTWS90")

  /** @group Constructors */
  case REQTWD90 extends KeywordName("REQTWD90", "REQTWD90")

  /** @group Constructors */
  case REQTWN90 extends KeywordName("REQTWN90", "REQTWN90")

  /** @group Constructors */
  case REQTWP90 extends KeywordName("REQTWP90", "REQTWP90")

  /** @group Constructors */
  case REQTWS91 extends KeywordName("REQTWS91", "REQTWS91")

  /** @group Constructors */
  case REQTWD91 extends KeywordName("REQTWD91", "REQTWD91")

  /** @group Constructors */
  case REQTWN91 extends KeywordName("REQTWN91", "REQTWN91")

  /** @group Constructors */
  case REQTWP91 extends KeywordName("REQTWP91", "REQTWP91")

  /** @group Constructors */
  case REQTWS92 extends KeywordName("REQTWS92", "REQTWS92")

  /** @group Constructors */
  case REQTWD92 extends KeywordName("REQTWD92", "REQTWD92")

  /** @group Constructors */
  case REQTWN92 extends KeywordName("REQTWN92", "REQTWN92")

  /** @group Constructors */
  case REQTWP92 extends KeywordName("REQTWP92", "REQTWP92")

  /** @group Constructors */
  case REQTWS93 extends KeywordName("REQTWS93", "REQTWS93")

  /** @group Constructors */
  case REQTWD93 extends KeywordName("REQTWD93", "REQTWD93")

  /** @group Constructors */
  case REQTWN93 extends KeywordName("REQTWN93", "REQTWN93")

  /** @group Constructors */
  case REQTWP93 extends KeywordName("REQTWP93", "REQTWP93")

  /** @group Constructors */
  case REQTWS94 extends KeywordName("REQTWS94", "REQTWS94")

  /** @group Constructors */
  case REQTWD94 extends KeywordName("REQTWD94", "REQTWD94")

  /** @group Constructors */
  case REQTWN94 extends KeywordName("REQTWN94", "REQTWN94")

  /** @group Constructors */
  case REQTWP94 extends KeywordName("REQTWP94", "REQTWP94")

  /** @group Constructors */
  case REQTWS95 extends KeywordName("REQTWS95", "REQTWS95")

  /** @group Constructors */
  case REQTWD95 extends KeywordName("REQTWD95", "REQTWD95")

  /** @group Constructors */
  case REQTWN95 extends KeywordName("REQTWN95", "REQTWN95")

  /** @group Constructors */
  case REQTWP95 extends KeywordName("REQTWP95", "REQTWP95")

  /** @group Constructors */
  case REQTWS96 extends KeywordName("REQTWS96", "REQTWS96")

  /** @group Constructors */
  case REQTWD96 extends KeywordName("REQTWD96", "REQTWD96")

  /** @group Constructors */
  case REQTWN96 extends KeywordName("REQTWN96", "REQTWN96")

  /** @group Constructors */
  case REQTWP96 extends KeywordName("REQTWP96", "REQTWP96")

  /** @group Constructors */
  case REQTWS97 extends KeywordName("REQTWS97", "REQTWS97")

  /** @group Constructors */
  case REQTWD97 extends KeywordName("REQTWD97", "REQTWD97")

  /** @group Constructors */
  case REQTWN97 extends KeywordName("REQTWN97", "REQTWN97")

  /** @group Constructors */
  case REQTWP97 extends KeywordName("REQTWP97", "REQTWP97")

  /** @group Constructors */
  case REQTWS98 extends KeywordName("REQTWS98", "REQTWS98")

  /** @group Constructors */
  case REQTWD98 extends KeywordName("REQTWD98", "REQTWD98")

  /** @group Constructors */
  case REQTWN98 extends KeywordName("REQTWN98", "REQTWN98")

  /** @group Constructors */
  case REQTWP98 extends KeywordName("REQTWP98", "REQTWP98")

  /** @group Constructors */
  case REQTWS99 extends KeywordName("REQTWS99", "REQTWS99")

  /** @group Constructors */
  case REQTWD99 extends KeywordName("REQTWD99", "REQTWD99")

  /** @group Constructors */
  case REQTWN99 extends KeywordName("REQTWN99", "REQTWN99")

  /** @group Constructors */
  case REQTWP99 extends KeywordName("REQTWP99", "REQTWP99")

  /** @group Constructors */
  case COADDS extends KeywordName("COADDS", "COADDS")

  /** @group Constructors */
  case EXPTIME extends KeywordName("EXPTIME", "EXPTIME")

  /** @group Constructors */
  case FILTER3 extends KeywordName("FILTER3", "FILTER3")

  /** @group Constructors */
  case FOCUSNAM extends KeywordName("FOCUSNAM", "FOCUSNAM")

  /** @group Constructors */
  case FOCUSPOS extends KeywordName("FOCUSPOS", "FOCUSPOS")

  /** @group Constructors */
  case FPMASK extends KeywordName("FPMASK", "FPMASK")

  /** @group Constructors */
  case BEAMSPLT extends KeywordName("BEAMSPLT", "BEAMSPLT")

  /** @group Constructors */
  case WINDCOVR extends KeywordName("WINDCOVR", "WINDCOVR")

  /** @group Constructors */
  case FRMSPCYCL extends KeywordName("FRMSPCYCL", "FRMSPCYC")

  /** @group Constructors */
  case HDRTIMING extends KeywordName("HDRTIMING", "HDRTIMIN")

  /** @group Constructors */
  case LNRS extends KeywordName("LNRS", "LNRS")

  /** @group Constructors */
  case MODE extends KeywordName("MODE", "MODE")

  /** @group Constructors */
  case NDAVGS extends KeywordName("NDAVGS", "NDAVGS")

  /** @group Constructors */
  case PVIEW extends KeywordName("PVIEW", "PVIEW")

  /** @group Constructors */
  case TDETABS extends KeywordName("TDETABS", "TDETABS")

  /** @group Constructors */
  case TIME extends KeywordName("TIME", "TIME")

  /** @group Constructors */
  case TMOUNT extends KeywordName("TMOUNT", "TMOUNT")

  /** @group Constructors */
  case UCODENAM extends KeywordName("UCODENAM", "UCODENAM")

  /** @group Constructors */
  case UCODETYP extends KeywordName("UCODETYP", "UCODETYP")

  /** @group Constructors */
  case VDDCL1 extends KeywordName("VDDCL1", "VDDCL1")

  /** @group Constructors */
  case VDDCL2 extends KeywordName("VDDCL2", "VDDCL2")

  /** @group Constructors */
  case VDDUC extends KeywordName("VDDUC", "VDDUC")

  /** @group Constructors */
  case VDET extends KeywordName("VDET", "VDET")

  /** @group Constructors */
  case VGGCL1 extends KeywordName("VGGCL1", "VGGCL1")

  /** @group Constructors */
  case VGGCL2 extends KeywordName("VGGCL2", "VGGCL2")

  /** @group Constructors */
  case VSET extends KeywordName("VSET", "VSET")

  /** @group Constructors */
  case A_TDETABS extends KeywordName("A_TDETABS", "A_TDETAB")

  /** @group Constructors */
  case A_TMOUNT extends KeywordName("A_TMOUNT", "A_TMOUNT")

  /** @group Constructors */
  case A_VDDCL1 extends KeywordName("A_VDDCL1", "A_VDDCL1")

  /** @group Constructors */
  case A_VDDCL2 extends KeywordName("A_VDDCL2", "A_VDDCL2")

  /** @group Constructors */
  case A_VDDUC extends KeywordName("A_VDDUC", "A_VDDUC")

  /** @group Constructors */
  case A_VDET extends KeywordName("A_VDET", "A_VDET")

  /** @group Constructors */
  case A_VGGCL1 extends KeywordName("A_VGGCL1", "A_VGGCL1")

  /** @group Constructors */
  case A_VGGCL2 extends KeywordName("A_VGGCL2", "A_VGGCL2")

  /** @group Constructors */
  case A_VSET extends KeywordName("A_VSET", "A_VSET")

  /** @group Constructors */
  case APOFFSET extends KeywordName("APOFFSET", "APOFFSET")

  /** @group Constructors */
  case FLIP extends KeywordName("FLIP", "FLIP")

  /** @group Constructors */
  case EXPRQ extends KeywordName("EXPRQ", "EXPRQ")

  /** @group Constructors */
  case DCNAME extends KeywordName("DCNAME", "DCNAME")

  /** @group Constructors */
  case PERIOD extends KeywordName("PERIOD", "PERIOD")

  /** @group Constructors */
  case NPERIODS extends KeywordName("NPERIODS", "NPERIODS")

  /** @group Constructors */
  case EXPMODE extends KeywordName("EXPMODE", "EXPMODE")

  /** @group Constructors */
  case BIASPWR extends KeywordName("BIASPWR", "BIASPWR")

  /** @group Constructors */
  case OBSMODE extends KeywordName("OBSMODE", "OBSMODE")

  /** @group Constructors */
  case RDTIME extends KeywordName("RDTIME", "RDTIME")

  /** @group Constructors */
  case CTYPE1 extends KeywordName("CTYPE1", "CTYPE1")

  /** @group Constructors */
  case CRPIX1 extends KeywordName("CRPIX1", "CRPIX1")

  /** @group Constructors */
  case CRVAL1 extends KeywordName("CRVAL1", "CRVAL1")

  /** @group Constructors */
  case CTYPE2 extends KeywordName("CTYPE2", "CTYPE2")

  /** @group Constructors */
  case CRPIX2 extends KeywordName("CRPIX2", "CRPIX2")

  /** @group Constructors */
  case CRVAL2 extends KeywordName("CRVAL2", "CRVAL2")

  /** @group Constructors */
  case CD1_1 extends KeywordName("CD1_1", "CD1_1")

  /** @group Constructors */
  case CD1_2 extends KeywordName("CD1_2", "CD1_2")

  /** @group Constructors */
  case CD2_1 extends KeywordName("CD2_1", "CD2_1")

  /** @group Constructors */
  case CD2_2 extends KeywordName("CD2_2", "CD2_2")

  /** @group Constructors */
  case RADECSYS extends KeywordName("RADECSYS", "RADECSYS")

  /** @group Constructors */
  case MJD_OBS extends KeywordName("MJD_OBS", "MJD_OBS")

  /** @group Constructors */
  case APERTURE extends KeywordName("APERTURE", "APERTURE")

  /** @group Constructors */
  case FILTER extends KeywordName("FILTER", "FILTER")

  /** @group Constructors */
  case AOFREQ extends KeywordName("AOFREQ", "AOFREQ")

  /** @group Constructors */
  case AOCOUNTS extends KeywordName("AOCOUNTS", "AOCOUNTS")

  /** @group Constructors */
  case AOSEEING extends KeywordName("AOSEEING", "AOSEEING")

  /** @group Constructors */
  case AOWFSX extends KeywordName("AOWFSX", "AOWFSX")

  /** @group Constructors */
  case AOWFSY extends KeywordName("AOWFSY", "AOWFSY")

  /** @group Constructors */
  case AOWFSZ extends KeywordName("AOWFSZ", "AOWFSZ")

  /** @group Constructors */
  case AOGAIN extends KeywordName("AOGAIN", "AOGAIN")

  /** @group Constructors */
  case AONCPAF extends KeywordName("AONCPAF", "AONCPAF")

  /** @group Constructors */
  case AONDFILT extends KeywordName("AONDFILT", "AONDFILT")

  /** @group Constructors */
  case AOFLENS extends KeywordName("AOFLENS", "AOFLENS")

  /** @group Constructors */
  case AOFLEXF extends KeywordName("AOFLEXF", "AOFLEXF")

  /** @group Constructors */
  case LGUSTAGE extends KeywordName("LGUSTAGE", "LGUSTAGE")

  /** @group Constructors */
  case AOBS extends KeywordName("AOBS", "AOBS")

  /** @group Constructors */
  case LGDFOCUS extends KeywordName("LGDFOCUS", "LGDFOCUS")

  /** @group Constructors */
  case LGTTCNTS extends KeywordName("LGTTCNTS", "LGTTCNTS")

  /** @group Constructors */
  case LGTTEXP extends KeywordName("LGTTEXP", "LGTTEXP")

  /** @group Constructors */
  case LGSFCNTS extends KeywordName("LGSFCNTS", "LGSFCNTS")

  /** @group Constructors */
  case LGSFEXP extends KeywordName("LGSFEXP", "LGSFEXP")

  /** @group Constructors */
  case FSMTIP extends KeywordName("FSMTIP", "FSMTIP")

  /** @group Constructors */
  case FSMTILT extends KeywordName("FSMTILT", "FSMTILT")

  /** @group Constructors */
  case LGZMPOS extends KeywordName("LGZMPOS", "LGZMPOS")

  /** @group Constructors */
  case NAALT extends KeywordName("NAALT", "NAALT")

  /** @group Constructors */
  case NATHICK extends KeywordName("NATHICK", "NATHICK")

  /** @group Constructors */
  case LGNDFILT extends KeywordName("LGNDFILT", "LGNDFILT")

  /** @group Constructors */
  case LGTTIRIS extends KeywordName("LGTTIRIS", "LGTTIRIS")

  /** @group Constructors */
  case ELAPSED extends KeywordName("ELAPSED", "ELAPSED")

  /** @group Constructors */
  case READDLAY extends KeywordName("READDLAY", "READDLAY")

  /** @group Constructors */
  case FILT1POS extends KeywordName("FILT1POS", "FILT1POS")

  /** @group Constructors */
  case FILT1CAR extends KeywordName("FILT1CAR", "FILT1CAR")

  /** @group Constructors */
  case FILT2POS extends KeywordName("FILT2POS", "FILT2POS")

  /** @group Constructors */
  case FILT2CAR extends KeywordName("FILT2CAR", "FILT2CAR")

  /** @group Constructors */
  case UTLWHEEL extends KeywordName("UTLWHEEL", "UTLWHEEL")

  /** @group Constructors */
  case UTLWPOS extends KeywordName("UTLWPOS", "UTLWPOS")

  /** @group Constructors */
  case UTLWCAR extends KeywordName("UTLWCAR", "UTLWCAR")

  /** @group Constructors */
  case CVERPOS extends KeywordName("CVERPOS", "CVERPOS")

  /** @group Constructors */
  case CVERCAR extends KeywordName("CVERCAR", "CVERCAR")

  /** @group Constructors */
  case CWSTEMP extends KeywordName("CWSTEMP", "CWSTEMP")

  /** @group Constructors */
  case DETTEMP extends KeywordName("DETTEMP", "DETTEMP")

  /** @group Constructors */
  case DETHTEMP extends KeywordName("DETHTEMP", "DETHTEMP")

  /** @group Constructors */
  case DEWPRES extends KeywordName("DEWPRES", "DEWPRES")

  /** @group Constructors */
  case RDNOISE extends KeywordName("RDNOISE", "RDNOISE")

  /** @group Constructors */
  case GAIN extends KeywordName("GAIN", "GAIN")

  /** @group Constructors */
  case SAMPMODE extends KeywordName("SAMPMODE", "SAMPMODE")

  /** @group Constructors */
  case NRESETS extends KeywordName("NRESETS", "NRESETS")

  /** @group Constructors */
  case RSTDLAY extends KeywordName("RSTDLAY", "RSTDLAY")

  /** @group Constructors */
  case READTIME extends KeywordName("READTIME", "READTIME")

  /** @group Constructors */
  case BUNIT extends KeywordName("BUNIT", "BUNIT")

  /** @group Constructors */
  case DCHLTH extends KeywordName("DCHLTH", "DCHLTH")

  /** @group Constructors */
  case DCSIM extends KeywordName("DCSIM", "DCSIM")

  /** @group Constructors */
  case DSPTIMBN extends KeywordName("DSPTIMBN", "DSPTIMBN")

  /** @group Constructors */
  case DSPTIMBV extends KeywordName("DSPTIMBV", "DSPTIMBV")

  /** @group Constructors */
  case DSPPCIN extends KeywordName("DSPPCIN", "DSPPCIN")

  /** @group Constructors */
  case DSPPCIV extends KeywordName("DSPPCIV", "DSPPCIV")

  /** @group Constructors */
  case GSAOI_MJD_OBS extends KeywordName("GSAOI_MJD_OBS", "MJD-OBS")

  /** @group Constructors */
  case GEMSSADC extends KeywordName("GEMSSADC", "GEMSSADC")

  /** @group Constructors */
  case GEMSDICH extends KeywordName("GEMSDICH", "GEMSDICH")

  /** @group Constructors */
  case GEMSASTR extends KeywordName("GEMSASTR", "GEMSASTR")

  /** @group Constructors */
  case GEMSNADC extends KeywordName("GEMSNADC", "GEMSNADC")

  /** @group Constructors */
  case LGWFS1CT extends KeywordName("LGWFS1CT", "LGWFS1CT")

  /** @group Constructors */
  case LGWFS2CT extends KeywordName("LGWFS2CT", "LGWFS2CT")

  /** @group Constructors */
  case LGWFS3CT extends KeywordName("LGWFS3CT", "LGWFS3CT")

  /** @group Constructors */
  case LGWFS4CT extends KeywordName("LGWFS4CT", "LGWFS4CT")

  /** @group Constructors */
  case LGWFS5CT extends KeywordName("LGWFS5CT", "LGWFS5CT")

  /** @group Constructors */
  case LGSLOOP extends KeywordName("LGSLOOP", "LGSLOOP")

  /** @group Constructors */
  case TTLOOP extends KeywordName("TTLOOP", "TTLOOP")

  /** @group Constructors */
  case FOCLOOP extends KeywordName("FOCLOOP", "FOCLOOP")

  /** @group Constructors */
  case FLEXLOOP extends KeywordName("FLEXLOOP", "FLEXLOOP")

  /** @group Constructors */
  case LGSSTRHL extends KeywordName("LGSSTRHL", "LGSSTRHL")

  /** @group Constructors */
  case RZEROVAL extends KeywordName("RZEROVAL", "RZEROVAL")

  /** @group Constructors */
  case CNSQARE1 extends KeywordName("CNSQARE1", "CNSQARE1")

  /** @group Constructors */
  case CNSQARE2 extends KeywordName("CNSQARE2", "CNSQARE2")

  /** @group Constructors */
  case CNSQARE3 extends KeywordName("CNSQARE3", "CNSQARE3")

  /** @group Constructors */
  case CNSQARE4 extends KeywordName("CNSQARE4", "CNSQARE4")

  /** @group Constructors */
  case CNSQARE5 extends KeywordName("CNSQARE5", "CNSQARE5")

  /** @group Constructors */
  case CNSQARE6 extends KeywordName("CNSQARE6", "CNSQARE6")

  /** @group Constructors */
  case GWFS1CFG extends KeywordName("GWFS1CFG", "GWFS1CFG")

  /** @group Constructors */
  case GWFS1OBJ extends KeywordName("GWFS1OBJ", "GWFS1OBJ")

  /** @group Constructors */
  case GWFS1RA extends KeywordName("GWFS1RA", "GWFS1RA")

  /** @group Constructors */
  case GWFS1DEC extends KeywordName("GWFS1DEC", "GWFS1DEC")

  /** @group Constructors */
  case GWFS1RV extends KeywordName("GWFS1RV", "GWFS1RV")

  /** @group Constructors */
  case GWFS1EPC extends KeywordName("GWFS1EPC", "GWFS1EPC")

  /** @group Constructors */
  case GWFS1EQN extends KeywordName("GWFS1EQN", "GWFS1EQN")

  /** @group Constructors */
  case GWFS1FRM extends KeywordName("GWFS1FRM", "GWFS1FRM")

  /** @group Constructors */
  case GWFS1PMD extends KeywordName("GWFS1PMD", "GWFS1PMD")

  /** @group Constructors */
  case GWFS1PMR extends KeywordName("GWFS1PMR", "GWFS1PMR")

  /** @group Constructors */
  case GWFS1PAR extends KeywordName("GWFS1PAR", "GWFS1PAR")

  /** @group Constructors */
  case GWFS1WAV extends KeywordName("GWFS1WAV", "GWFS1WAV")

  /** @group Constructors */
  case GWFS1X extends KeywordName("GWFS1X", "GWFS1X")

  /** @group Constructors */
  case GWFS1Y extends KeywordName("GWFS1Y", "GWFS1Y")

  /** @group Constructors */
  case GWFS1SIZ extends KeywordName("GWFS1SIZ", "GWFS1SIZ")

  /** @group Constructors */
  case GWFS1CTS extends KeywordName("GWFS1CTS", "GWFS1CTS")

  /** @group Constructors */
  case GWFS2CFG extends KeywordName("GWFS2CFG", "GWFS2CFG")

  /** @group Constructors */
  case GWFS2OBJ extends KeywordName("GWFS2OBJ", "GWFS2OBJ")

  /** @group Constructors */
  case GWFS2RA extends KeywordName("GWFS2RA", "GWFS2RA")

  /** @group Constructors */
  case GWFS2DEC extends KeywordName("GWFS2DEC", "GWFS2DEC")

  /** @group Constructors */
  case GWFS2RV extends KeywordName("GWFS2RV", "GWFS2RV")

  /** @group Constructors */
  case GWFS2EPC extends KeywordName("GWFS2EPC", "GWFS2EPC")

  /** @group Constructors */
  case GWFS2EQN extends KeywordName("GWFS2EQN", "GWFS2EQN")

  /** @group Constructors */
  case GWFS2FRM extends KeywordName("GWFS2FRM", "GWFS2FRM")

  /** @group Constructors */
  case GWFS2PMD extends KeywordName("GWFS2PMD", "GWFS2PMD")

  /** @group Constructors */
  case GWFS2PMR extends KeywordName("GWFS2PMR", "GWFS2PMR")

  /** @group Constructors */
  case GWFS2PAR extends KeywordName("GWFS2PAR", "GWFS2PAR")

  /** @group Constructors */
  case GWFS2WAV extends KeywordName("GWFS2WAV", "GWFS2WAV")

  /** @group Constructors */
  case GWFS2X extends KeywordName("GWFS2X", "GWFS2X")

  /** @group Constructors */
  case GWFS2Y extends KeywordName("GWFS2Y", "GWFS2Y")

  /** @group Constructors */
  case GWFS2SIZ extends KeywordName("GWFS2SIZ", "GWFS2SIZ")

  /** @group Constructors */
  case GWFS2CTS extends KeywordName("GWFS2CTS", "GWFS2CTS")

  /** @group Constructors */
  case GWFS3CFG extends KeywordName("GWFS3CFG", "GWFS3CFG")

  /** @group Constructors */
  case GWFS3OBJ extends KeywordName("GWFS3OBJ", "GWFS3OBJ")

  /** @group Constructors */
  case GWFS3RA extends KeywordName("GWFS3RA", "GWFS3RA")

  /** @group Constructors */
  case GWFS3DEC extends KeywordName("GWFS3DEC", "GWFS3DEC")

  /** @group Constructors */
  case GWFS3RV extends KeywordName("GWFS3RV", "GWFS3RV")

  /** @group Constructors */
  case GWFS3EPC extends KeywordName("GWFS3EPC", "GWFS3EPC")

  /** @group Constructors */
  case GWFS3EQN extends KeywordName("GWFS3EQN", "GWFS3EQN")

  /** @group Constructors */
  case GWFS3FRM extends KeywordName("GWFS3FRM", "GWFS3FRM")

  /** @group Constructors */
  case GWFS3PMD extends KeywordName("GWFS3PMD", "GWFS3PMD")

  /** @group Constructors */
  case GWFS3PMR extends KeywordName("GWFS3PMR", "GWFS3PMR")

  /** @group Constructors */
  case GWFS3PAR extends KeywordName("GWFS3PAR", "GWFS3PAR")

  /** @group Constructors */
  case GWFS3WAV extends KeywordName("GWFS3WAV", "GWFS3WAV")

  /** @group Constructors */
  case GWFS3X extends KeywordName("GWFS3X", "GWFS3X")

  /** @group Constructors */
  case GWFS3Y extends KeywordName("GWFS3Y", "GWFS3Y")

  /** @group Constructors */
  case GWFS3SIZ extends KeywordName("GWFS3SIZ", "GWFS3SIZ")

  /** @group Constructors */
  case GWFS3CTS extends KeywordName("GWFS3CTS", "GWFS3CTS")

  /** @group Constructors */
  case GWFS4CFG extends KeywordName("GWFS4CFG", "GWFS4CFG")

  /** @group Constructors */
  case GWFS4OBJ extends KeywordName("GWFS4OBJ", "GWFS4OBJ")

  /** @group Constructors */
  case GWFS4RA extends KeywordName("GWFS4RA", "GWFS4RA")

  /** @group Constructors */
  case GWFS4DEC extends KeywordName("GWFS4DEC", "GWFS4DEC")

  /** @group Constructors */
  case GWFS4RV extends KeywordName("GWFS4RV", "GWFS4RV")

  /** @group Constructors */
  case GWFS4EPC extends KeywordName("GWFS4EPC", "GWFS4EPC")

  /** @group Constructors */
  case GWFS4EQN extends KeywordName("GWFS4EQN", "GWFS4EQN")

  /** @group Constructors */
  case GWFS4FRM extends KeywordName("GWFS4FRM", "GWFS4FRM")

  /** @group Constructors */
  case GWFS4PMD extends KeywordName("GWFS4PMD", "GWFS4PMD")

  /** @group Constructors */
  case GWFS4PMR extends KeywordName("GWFS4PMR", "GWFS4PMR")

  /** @group Constructors */
  case GWFS4PAR extends KeywordName("GWFS4PAR", "GWFS4PAR")

  /** @group Constructors */
  case GWFS4WAV extends KeywordName("GWFS4WAV", "GWFS4WAV")

  /** @group Constructors */
  case GWFS4X extends KeywordName("GWFS4X", "GWFS4X")

  /** @group Constructors */
  case GWFS4Y extends KeywordName("GWFS4Y", "GWFS4Y")

  /** @group Constructors */
  case GWFS4SIZ extends KeywordName("GWFS4SIZ", "GWFS4SIZ")

  /** @group Constructors */
  case GWFS4CTS extends KeywordName("GWFS4CTS", "GWFS4CTS")

  /** @group Constructors */
  case NODMODE extends KeywordName("NODMODE", "NODMODE")

  /** @group Constructors */
  case NODPIX extends KeywordName("NODPIX", "NODPIX")

  /** @group Constructors */
  case NODCOUNT extends KeywordName("NODCOUNT", "NODCOUNT")

  /** @group Constructors */
  case NODAXOFF extends KeywordName("NODAXOFF", "NODAXOFF")

  /** @group Constructors */
  case NODAYOFF extends KeywordName("NODAYOFF", "NODAYOFF")

  /** @group Constructors */
  case NODBXOFF extends KeywordName("NODBXOFF", "NODBXOFF")

  /** @group Constructors */
  case NODBYOFF extends KeywordName("NODBYOFF", "NODBYOFF")

  /** @group Constructors */
  case ANODCNT extends KeywordName("ANODCNT", "ANODCNT")

  /** @group Constructors */
  case BNODCNT extends KeywordName("BNODCNT", "BNODCNT")

  /** @group Constructors */
  case SUBINT extends KeywordName("SUBINT", "SUBINT")

  /** @group Constructors */
  case BASEPO extends KeywordName("BASEPO", "BASEPO")

  /** @group Constructors */
  case SRIFU1 extends KeywordName("SRIFU1", "SRIFU1")

  /** @group Constructors */
  case SRIFU2 extends KeywordName("SRIFU2", "SRIFU2")

  /** @group Constructors */
  case HRIFU1 extends KeywordName("HRIFU1", "HRIFU1")

  /** @group Constructors */
  case HRIFU2 extends KeywordName("HRIFU2", "HRIFU2")

  /** @group Constructors */
  case IFU1GUID extends KeywordName("IFU1GUID", "IFU1GUID")

  /** @group Constructors */
  case IFU2GUID extends KeywordName("IFU2GUID", "IFU2GUID")

  /** @group Constructors */
  case FAGITAT1 extends KeywordName("FAGITAT1", "FAGITAT1")

  /** @group Constructors */
  case FAGITAT2 extends KeywordName("FAGITAT2", "FAGITAT2")

  /** @group Constructors */
  case NREDEXP extends KeywordName("NREDEXP", "NREDEXP")

  /** @group Constructors */
  case REDEXPT extends KeywordName("REDEXPT", "REDEXPT")

  /** @group Constructors */
  case NBLUEEXP extends KeywordName("NBLUEEXP", "NBLUEEXP")

  /** @group Constructors */
  case BLUEEXPT extends KeywordName("BLUEEXPT", "BLUEEXPT")

  /** @group Constructors */
  case NSLITEXP extends KeywordName("NSLITEXP", "NSLITEXP")

  /** @group Constructors */
  case SLITEXPT extends KeywordName("SLITEXPT", "SLITEXPT")

  /** @group Constructors */
  case REDCCDS extends KeywordName("REDCCDS", "REDCCDS")

  /** @group Constructors */
  case BLUCCDS extends KeywordName("BLUCCDS", "BLUCCDS")

  /** @group Constructors */
  case READRED extends KeywordName("READRED", "READRED")

  /** @group Constructors */
  case READBLU extends KeywordName("READBLU", "READBLU")

  /** @group Constructors */
  case TARGETM extends KeywordName("TARGETM", "TARGETM")

  /** @group Constructors */
  case RESOLUT extends KeywordName("RESOLUT", "RESOLUT")

  /** @group Constructors */
  case TEXPTIME extends KeywordName("TEXPTIME", "TEXPTIME")

  /** @group Constructors */
  case TARGET1 extends KeywordName("TARGET1", "TARGET1")

  /** @group Constructors */
  case TARGET2 extends KeywordName("TARGET2", "TARGET2")
  
}

object KeywordName {
  
  /** Select the member of KeywordName with the given tag, if any. */
  def fromTag(s: String): Option[KeywordName] = Enumerated.fromTag[KeywordName].getOption(s)

  /** Select the member of KeywordName with the given tag, throwing if absent. */
  def unsafeFromTag(s: String): KeywordName =
    fromTag(s).getOrElse(throw new NoSuchElementException(s"KeywordName: Invalid tag: '$s'"))
  
}
