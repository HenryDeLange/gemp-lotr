package com.gempukku.lotro.server.provider;

import com.gempukku.lotro.packs.*;
import org.apache.log4j.Logger;

import java.io.IOException;

public class PacksStorageBuilder {
    private static final Logger _logger = Logger.getLogger(PacksStorageBuilder.class);

    public static PacksStorage createPacksStorage() {
        try {
            PacksStorage packStorage = new PacksStorage();
            packStorage.addPackBox("FotR - League Starter", new LeagueStarterBox());
            packStorage.addPackBox("Random FotR Foil Common", new RandomFoilPack("C", new String[]{"1", "2", "3"}));
            packStorage.addPackBox("Random FotR Foil Uncommon", new RandomFoilPack("U", new String[]{"1", "2", "3"}));

            packStorage.addPackBox("(S)FotR - Starter", new FixedPackBox("(S)FotR - Starter"));
            packStorage.addPackBox("(S)MoM - Starter", new FixedPackBox("(S)MoM - Starter"));
            packStorage.addPackBox("(S)RotEL - Starter", new FixedPackBox("(S)RotEL - Starter"));

            packStorage.addPackBox("(S)TTT - Starter", new FixedPackBox("(S)TTT - Starter"));
            packStorage.addPackBox("(S)BoHD - Starter", new FixedPackBox("(S)BoHD - Starter"));
            packStorage.addPackBox("(S)EoF - Starter", new FixedPackBox("(S)EoF - Starter"));

            packStorage.addPackBox("(S)SH - Starter", new FixedPackBox("(S)SH - Starter"));
            packStorage.addPackBox("(S)BR - Starter", new FixedPackBox("(S)BR - Starter"));
            packStorage.addPackBox("(S)BL - Starter", new FixedPackBox("(S)BL - Starter"));

            packStorage.addPackBox("(S)RotK - Starter", new FixedPackBox("(S)RotK - Starter"));
            packStorage.addPackBox("(S)SoG - Starter", new FixedPackBox("(S)SoG - Starter"));
            packStorage.addPackBox("(S)MD - Starter", new FixedPackBox("(S)MD - Starter"));

            packStorage.addPackBox("(S)FotR - Tengwar", new TengwarPackBox(new int[]{1, 2, 3}));
            packStorage.addPackBox("(S)TTT - Tengwar", new TengwarPackBox(new int[]{4, 5, 6}));
            packStorage.addPackBox("(S)RotK - Tengwar", new TengwarPackBox(new int[]{7, 8, 10}));
            packStorage.addPackBox("(S)SH - Tengwar", new TengwarPackBox(new int[]{11, 12, 13}));
            packStorage.addPackBox("(S)Tengwar", new TengwarPackBox(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19}));

            packStorage.addPackBox("(S)Booster Choice", new FixedPackBox("(S)Booster Choice"));

            packStorage.addPackBox("FotR - Gandalf Starter", new FixedPackBox("FotR - Gandalf Starter"));
            packStorage.addPackBox("FotR - Aragorn Starter", new FixedPackBox("FotR - Aragorn Starter"));

            packStorage.addPackBox("MoM - Gandalf Starter", new FixedPackBox("MoM - Gandalf Starter"));
            packStorage.addPackBox("MoM - Gimli Starter", new FixedPackBox("MoM - Gimli Starter"));

            packStorage.addPackBox("RotEL - Boromir Starter", new FixedPackBox("RotEL - Boromir Starter"));
            packStorage.addPackBox("RotEL - Legolas Starter", new FixedPackBox("RotEL - Legolas Starter"));

            packStorage.addPackBox("TTT - Aragorn Starter", new FixedPackBox("TTT - Aragorn Starter"));
            packStorage.addPackBox("TTT - Theoden Starter", new FixedPackBox("TTT - Theoden Starter"));

            packStorage.addPackBox("BoHD - Eowyn Starter", new FixedPackBox("BoHD - Eowyn Starter"));
            packStorage.addPackBox("BoHD - Legolas Starter", new FixedPackBox("BoHD - Legolas Starter"));

            packStorage.addPackBox("EoF - Faramir Starter", new FixedPackBox("EoF - Faramir Starter"));
            packStorage.addPackBox("EoF - Witch-king Starter", new FixedPackBox("EoF - Witch-king Starter"));

            packStorage.addPackBox("RotK - Aragorn Starter", new FixedPackBox("RotK - Aragorn Starter"));
            packStorage.addPackBox("RotK - Eomer Starter", new FixedPackBox("RotK - Eomer Starter"));

            packStorage.addPackBox("SoG - Merry Starter", new FixedPackBox("SoG - Merry Starter"));
            packStorage.addPackBox("SoG - Pippin Starter", new FixedPackBox("SoG - Pippin Starter"));

            packStorage.addPackBox("MD - Frodo Starter", new FixedPackBox("MD - Frodo Starter"));
            packStorage.addPackBox("MD - Sam Starter", new FixedPackBox("MD - Sam Starter"));

            packStorage.addPackBox("SH - Aragorn Starter", new FixedPackBox("SH - Aragorn Starter"));
            packStorage.addPackBox("SH - Eowyn Starter", new FixedPackBox("SH - Eowyn Starter"));
            packStorage.addPackBox("SH - Gandalf Starter", new FixedPackBox("SH - Gandalf Starter"));
            packStorage.addPackBox("SH - Legolas Starter", new FixedPackBox("SH - Legolas Starter"));

            packStorage.addPackBox("BR - Mouth Starter", new FixedPackBox("BR - Mouth Starter"));
            packStorage.addPackBox("BR - Saruman Starter", new FixedPackBox("BR - Saruman Starter"));

            packStorage.addPackBox("BL - Arwen Starter", new FixedPackBox("BL - Arwen Starter"));
            packStorage.addPackBox("BL - Boromir Starter", new FixedPackBox("BL - Boromir Starter"));

            packStorage.addPackBox("FotR - Booster", new RarityPackBox(1));
            packStorage.addPackBox("MoM - Booster", new RarityPackBox(2));
            packStorage.addPackBox("RotEL - Booster", new RarityPackBox(3));

            packStorage.addPackBox("TTT - Booster", new RarityPackBox(4));
            packStorage.addPackBox("BoHD - Booster", new RarityPackBox(5));
            packStorage.addPackBox("EoF - Booster", new RarityPackBox(6));

            packStorage.addPackBox("RotK - Booster", new RarityPackBox(7));
            packStorage.addPackBox("SoG - Booster", new RarityPackBox(8));
            packStorage.addPackBox("MD - Booster", new RarityPackBox(10));

            packStorage.addPackBox("SH - Booster", new RarityPackBox(11));
            packStorage.addPackBox("BR - Booster", new RarityPackBox(12));
            packStorage.addPackBox("BL - Booster", new RarityPackBox(13));

            packStorage.addPackBox("HU - Booster", new RarityPackBox(15));
            packStorage.addPackBox("RoS - Booster", new RarityPackBox(17));
            packStorage.addPackBox("TaD - Booster", new RarityPackBox(18));

            packStorage.addPackBox("REF - Booster", new ReflectionsPackBox());

            return packStorage;
        } catch (IOException exp) {
            _logger.error("Error while creating resource", exp);
            exp.printStackTrace();
        } catch (RuntimeException exp) {
            _logger.error("Error while creating resource", exp);
            exp.printStackTrace();
        }
        return null;
    }
}