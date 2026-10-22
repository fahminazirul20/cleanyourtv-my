package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * JAKIM's 60 prayer-time zones (from api.waktusolat.app/zones, which rarely changes, so it is
 * bundled rather than fetched) and the matching state slug the Malaysia Calendar holidays API
 * (mycal-api.huijun00100101.workers.dev) expects. Backs the Prayer zone picker in Launcher settings,
 * so one build of this launcher works for any JAKIM zone, not just the one in config.xml.
 */
final class JakimZones {

    static final class Zone {
        final String code;
        final String state;
        final String district;

        Zone(String code, String state, String district) {
            this.code = code;
            this.state = state;
            this.district = district;
        }
    }

    private JakimZones() {
    }

    static List<Zone> all() {
        List<Zone> z = new ArrayList<>();
        z.add(new Zone("JHR01", "Johor", "Pulau Aur dan Pulau Pemanggil"));
        z.add(new Zone("JHR02", "Johor", "Johor Bahru, Kota Tinggi, Mersing, Kulai"));
        z.add(new Zone("JHR03", "Johor", "Kluang, Pontian"));
        z.add(new Zone("JHR04", "Johor", "Batu Pahat, Muar, Segamat, Gemas Johor, Tangkak"));
        z.add(new Zone("KDH01", "Kedah", "Kota Setar, Kubang Pasu, Pokok Sena"));
        z.add(new Zone("KDH02", "Kedah", "Kuala Muda, Yan, Pendang"));
        z.add(new Zone("KDH03", "Kedah", "Padang Terap, Sik"));
        z.add(new Zone("KDH04", "Kedah", "Baling"));
        z.add(new Zone("KDH05", "Kedah", "Bandar Baharu, Kulim"));
        z.add(new Zone("KDH06", "Kedah", "Langkawi"));
        z.add(new Zone("KDH07", "Kedah", "Puncak Gunung Jerai"));
        z.add(new Zone("KTN01", "Kelantan", "Bachok, Kota Bharu, Machang, Pasir Mas, Pasir Puteh, Tanah Merah, Tumpat, Kuala Krai"));
        z.add(new Zone("KTN02", "Kelantan", "Gua Musang, Jeli, Lojing"));
        z.add(new Zone("MLK01", "Melaka", "Seluruh Negeri Melaka"));
        z.add(new Zone("NGS01", "Negeri Sembilan", "Tampin, Jempol"));
        z.add(new Zone("NGS02", "Negeri Sembilan", "Jelebu, Kuala Pilah, Rembau"));
        z.add(new Zone("NGS03", "Negeri Sembilan", "Port Dickson, Seremban"));
        z.add(new Zone("PHG01", "Pahang", "Pulau Tioman"));
        z.add(new Zone("PHG02", "Pahang", "Kuantan, Pekan, Muadzam Shah"));
        z.add(new Zone("PHG03", "Pahang", "Jerantut, Temerloh, Maran, Bera, Chenor, Jengka"));
        z.add(new Zone("PHG04", "Pahang", "Bentong, Lipis, Raub"));
        z.add(new Zone("PHG05", "Pahang", "Genting Sempah, Janda Baik, Bukit Tinggi"));
        z.add(new Zone("PHG06", "Pahang", "Cameron Highlands, Genting Highlands, Bukit Fraser"));
        z.add(new Zone("PHG07", "Pahang", "Rompin, Endau, Pontian"));
        z.add(new Zone("PRK01", "Perak", "Tapah, Slim River, Tanjung Malim"));
        z.add(new Zone("PRK02", "Perak", "Kuala Kangsar, Sg. Siput, Ipoh, Batu Gajah, Kampar"));
        z.add(new Zone("PRK03", "Perak", "Lenggong, Pengkalan Hulu, Grik"));
        z.add(new Zone("PRK04", "Perak", "Temengor, Belum"));
        z.add(new Zone("PRK05", "Perak", "Kg Gajah, Teluk Intan, Bagan Datuk, Seri Iskandar, Beruas, Parit, Lumut, Sitiawan, Pulau Pangkor"));
        z.add(new Zone("PRK06", "Perak", "Selama, Taiping, Bagan Serai, Parit Buntar"));
        z.add(new Zone("PRK07", "Perak", "Bukit Larut"));
        z.add(new Zone("PLS01", "Perlis", "Seluruh Negeri Perlis"));
        z.add(new Zone("PNG01", "Pulau Pinang", "Seluruh Negeri Pulau Pinang"));
        z.add(new Zone("SBH01", "Sabah", "Sandakan, Bukit Garam, Semawang, Temanggong, Tambisan"));
        z.add(new Zone("SBH02", "Sabah", "Beluran, Telupid, Pinangah, Terusan, Kuamut"));
        z.add(new Zone("SBH03", "Sabah", "Lahad Datu, Silabukan, Kunak, Sahabat, Semporna, Tungku"));
        z.add(new Zone("SBH04", "Sabah", "Tawau, Balong, Merotai, Kalabakan"));
        z.add(new Zone("SBH05", "Sabah", "Kudat, Kota Marudu, Pitas, Pulau Banggi"));
        z.add(new Zone("SBH06", "Sabah", "Gunung Kinabalu"));
        z.add(new Zone("SBH07", "Sabah", "Kota Kinabalu, Ranau, Kota Belud, Tuaran, Penampang, Papar, Putatan"));
        z.add(new Zone("SBH08", "Sabah", "Pensiangan, Keningau, Tambunan, Nabawan"));
        z.add(new Zone("SBH09", "Sabah", "Beaufort, Kuala Penyu, Sipitang, Tenom, Long Pasia, Membakut, Weston"));
        z.add(new Zone("SWK01", "Sarawak", "Limbang, Lawas, Sundar, Trusan"));
        z.add(new Zone("SWK02", "Sarawak", "Miri, Niah, Bekenu, Sibuti, Marudi"));
        z.add(new Zone("SWK03", "Sarawak", "Pandan, Belaga, Suai, Tatau, Sebauh, Bintulu"));
        z.add(new Zone("SWK04", "Sarawak", "Sibu, Mukah, Dalat, Song, Igan, Oya, Balingian, Kanowit, Kapit"));
        z.add(new Zone("SWK05", "Sarawak", "Sarikei, Matu, Julau, Rajang, Daro, Bintangor, Belawai"));
        z.add(new Zone("SWK06", "Sarawak", "Lubok Antu, Sri Aman, Roban, Debak, Kabong, Lingga, Engkelili, Betong, Spaoh, Pusa, Saratok"));
        z.add(new Zone("SWK07", "Sarawak", "Serian, Simunjan, Samarahan, Sebuyau, Meludam"));
        z.add(new Zone("SWK08", "Sarawak", "Kuching, Bau, Lundu, Sematan"));
        z.add(new Zone("SWK09", "Sarawak", "Zon Khas (Kampung Patarikan)"));
        z.add(new Zone("SGR01", "Selangor", "Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, Shah Alam"));
        z.add(new Zone("SGR02", "Selangor", "Kuala Selangor, Sabak Bernam"));
        z.add(new Zone("SGR03", "Selangor", "Klang, Kuala Langat"));
        z.add(new Zone("TRG01", "Terengganu", "Kuala Terengganu, Marang, Kuala Nerus"));
        z.add(new Zone("TRG02", "Terengganu", "Besut, Setiu"));
        z.add(new Zone("TRG03", "Terengganu", "Hulu Terengganu"));
        z.add(new Zone("TRG04", "Terengganu", "Dungun, Kemaman"));
        z.add(new Zone("WLY01", "Wilayah Persekutuan", "Kuala Lumpur, Putrajaya"));
        z.add(new Zone("WLY02", "Wilayah Persekutuan", "Labuan"));
        return z;
    }

    static Zone byCode(String code) {
        for (Zone z : all()) if (z.code.equals(code)) return z;
        return null;
    }

    /** The state slug the holidays API (mycal-api) expects for this JAKIM zone's state. Falls back
     *  to "johor" if the zone is unrecognised, so the holiday card still shows something sensible. */
    static String holidayState(String zoneCode) {
        if (zoneCode == null) return "johor";
        if (zoneCode.startsWith("WLY02")) return "wp-labuan";
        if (zoneCode.startsWith("WLY")) return "kuala-lumpur";
        switch (zoneCode.length() >= 3 ? zoneCode.substring(0, 3) : "") {
            case "JHR": return "johor";
            case "KDH": return "kedah";
            case "KTN": return "kelantan";
            case "MLK": return "melaka";
            case "NGS": return "negeri-sembilan";
            case "PHG": return "pahang";
            case "PRK": return "perak";
            case "PLS": return "perlis";
            case "PNG": return "pulau-pinang";
            case "SBH": return "sabah";
            case "SWK": return "sarawak";
            case "SGR": return "selangor";
            case "TRG": return "terengganu";
            default: return "johor";
        }
    }
}
