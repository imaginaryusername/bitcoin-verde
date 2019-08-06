package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.address.fullnode.FullNodeAddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import org.bouncycastle.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class AddressProcessorTests extends IntegrationTest {

    public static TransactionId storeTransaction(final String hexString, final TransactionDatabaseManager transactionDatabaseManager) throws DatabaseException {
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(hexString));
        return transactionDatabaseManager.storeTransaction(transaction);
    }

    public static void storeFakeTransactionOutputs(final String transactionHashString, final int[] outputIndices, final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final Long transactionId = databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, version, lock_time) VALUES (?, ?, ?)")
                .setParameter(Sha256Hash.fromHexString(transactionHashString))
                .setParameter(Transaction.VERSION)
                .setParameter(LockTime.MAX_TIMESTAMP_VALUE)
        );
        for (final Integer outputIndex : outputIndices) {
            databaseConnection.executeSql(
                new Query("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)")
                    .setParameter(transactionId)
                    .setParameter(outputIndex)
                    .setParameter(Long.MAX_VALUE)
            );
        }
    }

    public static List<TransactionId> loadBvtTokens(final DatabaseManager databaseManager) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

        AddressProcessorTests.storeFakeTransactionOutputs("C5498D00002572CA9690A3520E5D12868E06BC1ED5672F5A4413C64C3F67F16A", new int[]{ 0 }, databaseManager);
        AddressProcessorTests.storeFakeTransactionOutputs("9405B76E7984D10F8079B228D1E769F103A47C8ED50A840E03514D1185655ED7", new int[]{ 0 }, databaseManager);
        AddressProcessorTests.storeFakeTransactionOutputs("5AAF7C3902169B5FF34A810576C46B6FA4BA48A432D7707DFCFD33FB945C1928", new int[]{ 1 }, databaseManager);
        AddressProcessorTests.storeFakeTransactionOutputs("F1E64CCA0740142F071131566D9B3828B4C25DD640F0A19C9A56800875CDCE2F", new int[]{ 1 }, databaseManager);

        // BCH Tx: A64EF604F0DAC3F018F97CE654A0E2F02CD93A75E52819F499C49B69F92886C3
        AddressProcessorTests.storeTransaction("02000000012FCECD750880569A9CA1F040D65DC2B428389B6D563111072F144007CA4CE6F1010000006B483045022100AF01AE47B4876E0F4BE37050C7D712247C8C5EC834CFB80FDC4B0E608E120F44022001687BB3B3D9A6A30E005EBB26BB36DC6CFABBB2775BBA2A2E07D2FFA99BD7434121030C2FA571BCFF78A515D8F5690A405DBE3D6B5A15C2B10961741E593357D5E2F9FFFFFFFF0210270000000000001976A914C972C41371788F04F370A09A43100AE9A1E182EA88ACD05E0000000000001976A914D5B870B5FAAFCF4AE631DE034DE70257D737259288AC00000000", transactionDatabaseManager);

        // BCH Tx: 60886A9756C0A9CD2EECFE9C2D0297D5D6F2BA9DD38362F3D31412A1D6D149AF
        AddressProcessorTests.storeTransaction("02000000026AF1673F4CC613445A2F67D51EBC068E86125D0E52A39096CA722500008D49C5000000006B483045022100E5FDB491A1EC75CA53E675278F4C61163FA9BA0A52F094F4F8358A8EA61395E1022064DBC56218E0F461462FF9AA95C183923DEBD6F04319CD85A40A664F17929A994121038D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CFFFFFFFFD75E6585114D51030E840AD58E7CA403F169E7D128B279800FD184796EB70594000000006B4830450221009E3B9AC058E4A0F7F1A6EFFDD5C0B65F4C42B4731E97A6E2C89EBB427A5A904202204CABA61AFBB302C8DAE148E5543D09D3B4FD4C7D0B19BBD32ED9A8CCDF19E7024121038D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CFFFFFFFF02204E0000000000001976A914FC2B2B044B55604B87AFFFDEDB4C7C581C76728688AC5A250000000000001976A91455CE63AE0472184AE53B26BB117D6843C667BA3388AC00000000", transactionDatabaseManager);

        // BCH Tx: E6EDCF4842A95138B4256CA304017F0FFDD01ADF92A74A67061287105BAE9F1F
        AddressProcessorTests.storeTransaction("0200000001C38628F9699BC499F41928E5753AD92CF0E2A054E67CF918F0C3DAF004F64EA6010000006B483045022100EC7905682984B495B12E4BA5C5CC9A2D25CCB439A7E6EA04A59304EFD27D818102201E034D67A11E123A58F144B6F918A9EE50F340ABB5E80E5979E0E9D298FB8C5B412103907F871DBDDA8FAC8364B233D7FDA0269A1F043A8EBA17D18906FD50F01FBF54FFFFFFFF0220030000000000001976A914BFB6E05320F41FE853D486DE7192F1E42E22074888ACAE5A0000000000001976A914721F7AB5417398F335FB8B11A5A7F7D72D80FAD788AC00000000", transactionDatabaseManager);

        // BCH Tx: 5353A1069E04D43FEE09B32C2B7589D76E39E0D9BE41B30EC5A846DD477CF542
        AddressProcessorTests.storeTransaction("02000000011F9FAE5B10871206674AA792DF1AD0FD0F7F0104A36C25B43851A94248CFEDE6010000006B483045022100D60ED527D2B0C1BB8673D0CE9E881C00FEF0DC868647DF0711E1725BF8405F1402207067C93DED2DB3F76DDEBD6BE17BEB6A83BE1CED024516A194796E1C56E4DA50412102E21AD18FF8F535EC5315FEA5E2DE19BEE20B9B2FDD203645276CCC5603ED68F5FFFFFFFF02D0070000000000001976A914BFB6E05320F41FE853D486DE7192F1E42E22074888ACDC510000000000001976A91470469FA67E81375C6BDB97E54D3CA31C5C17234788AC00000000", transactionDatabaseManager);

        // BCH Tx: B9C86AFB9027C041ABA93022991C895D68C0A0EFBF73F1A35BBD7A9164159FE2
        AddressProcessorTests.storeTransaction("020000000142F57C47DD46A8C50EB341BED9E0396ED789752B2CB309EE3FD4049E06A15353010000006A47304402200DF889516697DB28EC1CD6BA63F89E6E8B84270054B1DE558B6A54B9099956FE02203830A3035B9E9898A1682B46D42736EA9C3D42AED305248B11DE6FD467EC2A3F4121038B192ED2A47B8EF04F9BE19A0F751C78607C4098A0AD3B08ADF290873F5E6681FFFFFFFF02D0070000000000001976A914BFB6E05320F41FE853D486DE7192F1E42E22074888AC0A490000000000001976A914CF18AFC96A061A415FCBAB8D97B25ADCEDDB5A7788AC00000000", transactionDatabaseManager);

        // BCH Tx: DFB097581C42EC941289A9D1C839B8668A253F7BB7AF9FB1AAFB5A5875509DDA
        AddressProcessorTests.storeTransaction("020000000128195C94FB33FDFC7D70D732A448BAA46F6BC47605814AF35F9B1602397CAF5A010000006B483045022100C27A784ECE8CE7ABD3327D134F3026746B9DC618336BB37BD05089739AB9F0800220253439A5E88F92D991452830B82DE09031DB1EBE1F1E2F253486ECB06232BEEA412102E5D61BD11A3CE8BFA4A92BA1426B48860CB13EB5806B1DFA494924EFFE0D1DE5FFFFFFFF02D0070000000000001976A9141201DFAAE844B61B76517EB04969FCFE6518CE7F88AC66370000000000001976A91453E7C4E2765BFF45BDD5A99FF10D2F2E0BC2E79D88AC00000000", transactionDatabaseManager);

        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>();

        // BVT Genesis
        // 34DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0
        transactionIds.add(AddressProcessorTests.storeTransaction("0200000002AF49D1D6A11214D3F36283D39DBAF2D6D597022D9CFEEC2ECDA9C056976A8860010000008A47304402200F78E36957818613EAAC9D2AEC24ED241BE073369342DB459F531363F3D634D6022068142982090BDAD9D7F58B746B0302073DCDC2D0888B7718D16957FAA7B52CB24141048D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CB4AA21A1EE9AC97B53769935A2C33D10006768813C6039F38BBBA0DA3BB10CE9FFFFFFFFAF49D1D6A11214D3F36283D39DBAF2D6D597022D9CFEEC2ECDA9C056976A886000000000694630430220221DEECB0DA419DB079C2EA2A5B3859BCEBD92B41DC073467E22C0B03A914C16021F4EBB42FC2E719185BBDD6D9583A14D492882FD7A3784921DA9C780D5E535F04121029AEABFB7D24D3360C965EC4C0732357657EE4183660BB0BC050C0DE22086F53CFFFFFFFF0400000000000000004F6A04534C500001010747454E455349530342565412426974636F696E20566572646520546573741868747470733A2F2F626974636F696E76657264652E6F72674C000108010208000775F05A07400010270000000000001976A9142297636D6AF0116B6467DCF7C22DC2CAFBC3B3F188AC10270000000000001976A9142297636D6AF0116B6467DCF7C22DC2CAFBC3B3F188AC2A230000000000001976A9145A3DEC87180CC8994907D3B16F1232E8B4EB9E1988AC00000000", transactionDatabaseManager));

        // BVT Mint
        // 97BB8FFE6DC71AC5B263F322056069CF398CDA2677E21951364F00D2D572E887
        transactionIds.add(AddressProcessorTests.storeTransaction("0200000001D02E2F9612CED76730DA004F40522F6CE4C115380C284FFCA8BBC5F0E82FDD34020000006A473044022005EDF960917BCA862C53A60D27A2C0039BF3EC508210B441D3342C953608082602204CD6D425593D1566B2F8620876EC840DBB95881A24EC030064C2221064C863E24121029602CA68608F9AB02DC2DA445C97F2D3980F75A5C620742C309BDD8B7E5A5B64FFFFFFFF040000000000000000396A04534C50000101044D494E542034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0010208000775F05A07400082020000000000001976A914B3BE4593503F84E2BE61EB33670B31CC7F4FC0AE88AC82020000000000001976A9142297636D6AF0116B6467DCF7C22DC2CAFBC3B3F188ACA6200000000000001976A9145A3DEC87180CC8994907D3B16F1232E8B4EB9E1988AC00000000", transactionDatabaseManager));

        // BVT Invalid Send (Poorly Formatted SLP Script) (Not Recorded as a SLP Transaction)
        // 16EA62D94AC142BAF93A6C44C5DC961883DC4D38B85F737ED5B7BB326707C647
        transactionIds.add(AddressProcessorTests.storeTransaction("0200000001D02E2F9612CED76730DA004F40522F6CE4C115380C284FFCA8BBC5F0E82FDD34010000006B483045022100BB28305E15A28901E129908714E4E40714F368875344DEF14354791C563D3EE90220509498A93BFFBD805D9EAE25E3C74909FD5C92B522620C6833E13EB8307E8D364121029602CA68608F9AB02DC2DA445C97F2D3980F75A5C620742C309BDD8B7E5A5B64FFFFFFFF040000000000000000416A04534C50000101055350454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0080000000005F5E10008000775F054115F0082020000000000001976A914B3BE4593503F84E2BE61EB33670B31CC7F4FC0AE88AC82020000000000001976A9142297636D6AF0116B6467DCF7C22DC2CAFBC3B3F188AC9E200000000000001976A9145A3DEC87180CC8994907D3B16F1232E8B4EB9E1988AC00000000", transactionDatabaseManager));

        // BVT Invalid Send (Insufficient SLP Inputs) (Still Recorded as a SLP Transaction)
        // 9BD457D106B1EECBD43CD6ECA0A993420ABE16075B05012C8A76BB96D1AE16CE
        transactionIds.add(AddressProcessorTests.storeTransaction("0200000001DA9D5075585AFBAAB19FAFB77B3F258A66B839C8D1A9891294EC421C5897B0DF010000006B4830450221009DA3A68910435B69E17CB848BA6EC603A2F3051130986ECA724E45FEE049734802206D8F42EFE44CDEA7EA2A7A31849ED0D8B27DB24AD9E38F304B30C69751686A684121029CBCE30D90DDFD1B530ABD63C9A7C49999BD5888DA09C7D05667CAB476697B6CFFFFFFFF020000000000000000376A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000000000000F24360000000000001976A91453E7C4E2765BFF45BDD5A99FF10D2F2E0BC2E79D88AC00000000", transactionDatabaseManager));

        // BVT Send
        // 8572AA67141E5FB6C48557508D036542AAD99C828F22B429612BDCABBAD95373
        transactionIds.add(AddressProcessorTests.storeTransaction("020000000287E872D5D2004F365119E27726DA8C39CF69600522F363B2C51AC76DFE8FBB97010000008B483045022100CF9D56AAEB53684473B7A005C8C78950E0FA7473A9A61C44156FAC8FF1131C4C02203EECC442E37BDB8E07E974930C02B1DB3A59A36809137489CBF9E93597CE407E4141049602CA68608F9AB02DC2DA445C97F2D3980F75A5C620742C309BDD8B7E5A5B640FD0D5AF4A8168711728B672F2AE0675CFD4E7B67F7EC1E5C6F2EB4BD2E61782FFFFFFFF87E872D5D2004F365119E27726DA8C39CF69600522F363B2C51AC76DFE8FBB97030000006A47304402200C92A84481C4B15A8E138EB4E7E1087464A370BFC7C6C3F00A20A3187088600F02206BB4E38D9A8540A3F64EA9F9F5F537E68D038A8AC62B3B59C663B1C50B7F30414121038D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CFFFFFFFF040000000000000000406A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0080000000005F5E10008000775F054115F0082020000000000001976A914B3BE4593503F84E2BE61EB33670B31CC7F4FC0AE88AC82020000000000001976A9142297636D6AF0116B6467DCF7C22DC2CAFBC3B3F188AC031C0000000000001976A9145A3DEC87180CC8994907D3B16F1232E8B4EB9E1988AC00000000", transactionDatabaseManager));

        // BVT Send
        // 68092D36527D174CEA76797B3BB2677F61945FDECA01710976BF840664F7B71A
        transactionIds.add(AddressProcessorTests.storeTransaction("02000000057353D9BAABDC2B6129B4228F829CD9AA4265038D505785C4B65F1E1467AA7285010000008A47304402204B3C5691676C5B417CF7DD07C83CDE900996DDB1BC7C7B2C0D7DD97161D360D1022041628D687EA100FAC4FB6A30AC677494055481082852CE7867AC3E0AECA8BD414141049602CA68608F9AB02DC2DA445C97F2D3980F75A5C620742C309BDD8B7E5A5B640FD0D5AF4A8168711728B672F2AE0675CFD4E7B67F7EC1E5C6F2EB4BD2E61782FFFFFFFF47C6076732BBB7D57E735FB8384DDC831896DCC5446C3AF9BA42C14AD962EA16010000008A47304402204493D84EE7A8A4187FBA5EC7C793C735CE8272B36617C2B0B6AB42DF0F68816D02202548D647FC9448D547E80E502D7ECFBCC3E4ED385985DC5F065CB8AE6974B7354141049602CA68608F9AB02DC2DA445C97F2D3980F75A5C620742C309BDD8B7E5A5B640FD0D5AF4A8168711728B672F2AE0675CFD4E7B67F7EC1E5C6F2EB4BD2E61782FFFFFFFF47C6076732BBB7D57E735FB8384DDC831896DCC5446C3AF9BA42C14AD962EA16020000006B483045022100CFC87A21C06AA5BAED2C533DBF5DF3A0AA0699616B389DD1069917DBED37990902206A212DA1FDA7E8CA040B1BB64D15F01A8DD1002C7B2CE524100D9C0AA28F2CF74121029602CA68608F9AB02DC2DA445C97F2D3980F75A5C620742C309BDD8B7E5A5B64FFFFFFFF7353D9BAABDC2B6129B4228F829CD9AA4265038D505785C4B65F1E1467AA7285030000006B483045022100D24F214DDA7A7B0E94C522961BE89313D6348E6935FFA31CEFE3BF2F794824E4022023631DCB12F0E9C89DDAB09BD788517542CF8DCE6D8AB1C4EC1569509BEE572B4121038D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CFFFFFFFF47C6076732BBB7D57E735FB8384DDC831896DCC5446C3AF9BA42C14AD962EA16030000006A47304402203C078DB46E37E6601190B6F870F858C293E153AB925D28208D946BF551E90F94022004C37DE8605889CB99E515D7BE581366DDE3691A7E600F0A89E09E170E1CF0654121038D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CFFFFFFFF030000000000000000376A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000000000C35010270000000000001976A91468C0E51658F1C0945B58D6225071B03C590107DD88AC05190000000000001976A91487077F24A8AE86E2D76872486D1C820A87BB3EC788AC00000000", transactionDatabaseManager));

        // BVT Send
        // 0F58E80BF3E747E32BCF3218D77DC01495622D723589D1F1D1FD98AEFA798D3D
        transactionIds.add(AddressProcessorTests.storeTransaction("02000000011AB7F7640684BF76097101CADE5F94617F67B23B7B7976EA4C177D52362D0968010000008A4730440220338FC724CEA49B7349036370C5998278B4CCA078C4065D1B23A206E2B2714CD002206BA0A72EE7CE3111102813A1D1BD7DC9C24923149E11D6D9F53ECF812C7058464141049AEABFB7D24D3360C965EC4C0732357657EE4183660BB0BC050C0DE22086F53CA3ED6C23F4FD932083C073F3E0271178CC7999DFF73BB36F732595785B1055FAFFFFFFFF030000000000000000406A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0080000000000001E6108000000000000A4EF611E0000000000001976A914056B3F4EADA24CBE80F27E4A6B4BECD58BA6D91888AC42070000000000001976A91487077F24A8AE86E2D76872486D1C820A87BB3EC788AC00000000", transactionDatabaseManager));

        // BVT Send
        // 4C27492AA05C9D4248ADF3DA47A9915FB0694D00D01462FF48B461E36486DE99
        transactionIds.add(AddressProcessorTests.storeTransaction("02000000021AB7F7640684BF76097101CADE5F94617F67B23B7B7976EA4C177D52362D0968020000008A473044022042D99CB0FB14E2BF7362CA9DF8BC85A9ABDF4D6D4DD3CAE9F9DEB9109D74CA2002203723F721D585F694B4BE9651BFE12383C58170A98379B886909049EFEAFE03354141040802CA060816C5596860EEB2C3237DBABFFC5A5EBD58F1B6EB48CE08E213DBA7069B638863C81EFF15FCE23183AD337D12E4D29A794652FC74436CDC09E92C17FFFFFFFF7353D9BAABDC2B6129B4228F829CD9AA4265038D505785C4B65F1E1467AA7285020000006A47304402206B73DF0271AA3B56C40B50AFE3705F5AB34212FADC6DDC3144F689C135F625FC0220277DB11148A9A57900BC1964F49D41B4AA1E511B599CE1D216C9ABD68056966F4121029602CA68608F9AB02DC2DA445C97F2D3980F75A5C620742C309BDD8B7E5A5B64FFFFFFFF040000000000000000496A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000001DCD650008000000001DCD650008000775F01876950022020000000000001976A91464535EED5C5B37142B07EDC67249394A4F1D8BAC88AC22020000000000001976A914BFB6E05320F41FE853D486DE7192F1E42E22074888AC19150000000000001976A91468C0E51658F1C0945B58D6225071B03C590107DD88AC00000000", transactionDatabaseManager));

        // BVT Send
        // 87B17979CC05E9E5F5FA9E8C6D78482478A4E6F6D78360E818E16311F7F157F0
        transactionIds.add(AddressProcessorTests.storeTransaction("020000000299DE8664E361B448FF6214D0004D69B05F91A947DAF3AD48429D5CA02A49274C020000006A473044022042B544B0BB247CB989223BA4789920DCFF72FE25F2A462748C374AA18419A4E302203AC315699B63306010B3D702922AF0D85AE29A342C83030263F62A67A70B97D44121024C9E1A480EF0C240488D55FE3D6195146E11F62C0476F361452EDD73A6FB581BFFFFFFFF42F57C47DD46A8C50EB341BED9E0396ED789752B2CB309EE3FD4049E06A15353000000006B483045022100C160CB338DDF79986DAC93751E804D63D929EF19C829EE76F1F05597D8744B8B022003D3CD2E75689C989C24D743D90235BBF39C6C7217B59F07EEBDB645CB9F44E64121024C9E1A480EF0C240488D55FE3D6195146E11F62C0476F361452EDD73A6FB581BFFFFFFFF030000000000000000406A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000000000000F08000000001DCD64F182020000000000001976A9145F2312B40B6EEEE6BB33619DD89CBCBDC9D56C7088AC4F050000000000001976A914D6158E26E39E19C2F3A7D78FAF7817C0E505E69088AC00000000", transactionDatabaseManager));

        // BVT Send
        // 731B7493DCAF21A368F384D75AD820F73F72DE9479622B35EF935E5D5C9D6F0E
        transactionIds.add(AddressProcessorTests.storeTransaction("0200000002F057F1F71163E118E86083D7F6E6A4782448786D8C9EFAF5E5E905CC7979B187020000008B483045022100DE9A1EDDA9F1CBD81A1CF7DBC9D54EB69E0A57589F0989387B917AFB2A2D942002206DB26ED916A041EDFA710202B23A2BBF9B66C57F067140AAD68A4B582F1734E74141044C9E1A480EF0C240488D55FE3D6195146E11F62C0476F361452EDD73A6FB581BC38FFD45314B4085449E0F8BB3EE6337F9EEBFF51F7F6E761882E545DD9BA930FFFFFFFF1F9FAE5B10871206674AA792DF1AD0FD0F7F0104A36C25B43851A94248CFEDE6000000006A47304402202217FD73378E2F80162FAAB324B54E2F81701AA7BF7AAF48CB4BC4EF7B4EE34D022074A372ABCA7FADCF89D9D5E21BFD3BABCEB320840F1BEEF5CFA72ED152EE5F484121024C9E1A480EF0C240488D55FE3D6195146E11F62C0476F361452EDD73A6FB581BFFFFFFFF030000000000000000406A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000000000002808000000001DCD64C982020000000000001976A9140E922F2E3DE8719503978B781E8005D63965D1FE88AC82020000000000001976A914D6158E26E39E19C2F3A7D78FAF7817C0E505E69088AC00000000", transactionDatabaseManager));

        // BVT Send
        // AE0D9AE505E4B75619A376FA70F7C295245F8FD28F3B625FBEA19E26AB29A928
        transactionIds.add(AddressProcessorTests.storeTransaction("02000000020E6F9D5C5D5E93EF352B627994DE723FF720D85AD784F368A321AFDC93741B73020000008A4730440220252C74C5BACF42B3BA91EF979590F90F6D15BAB742A19FEA1B5210F157832FF20220264C54F7F1CFFABEADC2B3EEFE9AAFD105737280561B78560F02B2AAA5788E864141044C9E1A480EF0C240488D55FE3D6195146E11F62C0476F361452EDD73A6FB581BC38FFD45314B4085449E0F8BB3EE6337F9EEBFF51F7F6E761882E545DD9BA930FFFFFFFFE29F1564917ABD5BA3F173BFEFA0C0685D891C992230A9AB41C02790FB6AC8B9000000006B483045022100BE141742CD4AF848C74C2735095ADB7AB23658D9D3A73CAA949CD5DC9CE57DC702202A51529DC7A06FD4F53163C340D83306CE2F84B73E4F0A76807AE73EBB2634B54121024C9E1A480EF0C240488D55FE3D6195146E11F62C0476F361452EDD73A6FB581BFFFFFFFF030000000000000000406A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000000000000F08000000001DCD64BA82020000000000001976A914FBF58EA33553EBBE7F94BDA75BB1B27C381D809B88ACAF050000000000001976A914D6158E26E39E19C2F3A7D78FAF7817C0E505E69088AC00000000", transactionDatabaseManager));

        // BCH Tx: 4F2B5CAC1B7465C86794134FBCBA031F6B8A5ADB266994CDABDFBFD388500E70
        AddressProcessorTests.storeTransaction("020000000228A929AB269EA1BE5F623B8FD28F5F2495C2F770FA76A31956B7E405E59A0DAE010000006A47304402202AD8882E4E7402B6DAE0E72EFA7839A43DDE7B3CD3EAC8BED2B498ED05ADBA6902202866A88FA981064657E95D6A22E4847CFC30EB09744A030D4C5D9EAC9A680C16412103C6582FD5CF818CA66C9E95C6534428FA139DE68F693AEF72B90F1F0F0089A4F5FFFFFFFF0E6F9D5C5D5E93EF352B627994DE723FF720D85AD784F368A321AFDC93741B73010000006A47304402204E0ACF261052DDEB770297B5D440F54F52D239BAC0F5816B21CDDCEF11AFE59E02204F6D79E10A3DB8E5A64C69DDBAE3647CACFB098ACFEB20BC21224ACD98DA17B1412103CF0F551958546174A24A1ECCD091C5D288CFD0871DA12DBA018D1B91DA00F49EFFFFFFFF0122020000000000001976A91415E0A3C870799DE9823C3F4433A283A9731B1DDD88AC00000000", transactionDatabaseManager);

        // BVT Invalid Send (Insufficient SLP Inputs) (Still Recorded as a SLP Transaction)
        // 08937051BA961330600D382A749262753B8A941E9E155BA9798D2922C2CE3842
        transactionIds.add(AddressProcessorTests.storeTransaction("0200000003F057F1F71163E118E86083D7F6E6A4782448786D8C9EFAF5E5E905CC7979B187010000006B483045022100D33AEE948D5AD56FBDC3EEDA11B55FED40D1D623086A7987AC3CFAEA0267F834022039F986425F2982D0E06039B1ABCFEF384281914D1B1274CE468D4556A7687A9E4121038EEF79415708264AF600AEC113C83BBC98288E1C6370F6DC0B61BC73408DEC59FFFFFFFF99DE8664E361B448FF6214D0004D69B05F91A947DAF3AD48429D5CA02A49274C010000006B483045022100B5CC2D1BEC4401796D0311AC5B8116FDFD8AAF514ED5AB1AD85757A12E3AE50E02202DF848135E54380A29A4212BDCE691645DEF548174AB1A659DA3BFEA168E4DD1412102EF9BCABA5380573B78508C02BC6545094BD2B4135D39202771105C9913DF6B05FFFFFFFFCE16AED196BB768A2C01055B0716BE0A4293A9A0ECD63CD4CBEEB106D157D49B010000006B483045022100B6163B97EA96193F274DA6F4D70C0216555AAB866083DB4DB2AED1DB96BCD01902207D362ED2CE615A4EE652F2243F2132AB865552DF031334AB3DC90C65075F0A2C4121029CBCE30D90DDFD1B530ABD63C9A7C49999BD5888DA09C7D05667CAB476697B6CFFFFFFFF020000000000000000376A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000001DCD651EB0360000000000001976A91415E0A3C870799DE9823C3F4433A283A9731B1DDD88AC00000000", transactionDatabaseManager));

        // BVT Invalid Send (Previous Input Is Invalid)
        // 9DF13E226887F408207F94E99108706B55149AF8C8EB9D2F36427BA3007DCD64
        transactionIds.add(AddressProcessorTests.storeTransaction("02000000024238CEC222298D79A95B159E1E948A3B756292742A380D60301396BA51709308010000006A473044022013121F277F1E1A66D9B202518773483056BCB90A3EA0E8E017E42B3B88A93374022036FC7D602C91CE14C01B0E08C30F02C71F9339CBB98D3FC10499F27BF017657D41210306CA102E0633D379AFA3DD3D90A6FDDDFA336B76827E5647D4CD516E705F2D40FFFFFFFF700E5088D3BFDFABCD946926DB5A8A6B1F03BABC4F139467C865741BAC5C2B4F000000006A47304402206ACDA67F1EC61D79B684CE6772E14D801A5A817C3E07B194FD34A5C612E6B24C02200D8932EC6387F2789361BA1FF769E0E235F381D83AD9E319BD410992EDFC52F941210306CA102E0633D379AFA3DD3D90A6FDDDFA336B76827E5647D4CD516E705F2D40FFFFFFFF020000000000000000376A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000001DCD651EB6350000000000001976A91415E0A3C870799DE9823C3F4433A283A9731B1DDD88AC00000000", transactionDatabaseManager));

        // BVT Invalid Send (Previous Input Is Invalid)
        // 25039E1E154AD0D0ED632AF5A6524898540EE8B310B878045343E8D93D7B88C1
        transactionIds.add(AddressProcessorTests.storeTransaction("020000000164CD7D00A37B42362F9DEBC8F89A14556B700891E9947F2008F48768223EF19D010000006B483045022100C123B3A219ECB38F712EDC47C3D68C712A038927E419A70C6B7847D6DE33FC4D02201EF816223AAF4A5D9F6703C0AF211DA476F10FD81B6D170F3C97CD34D2A3882241210306CA102E0633D379AFA3DD3D90A6FDDDFA336B76827E5647D4CD516E705F2D40FFFFFFFF020000000000000000376A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000001DCD651E74340000000000001976A91415E0A3C870799DE9823C3F4433A283A9731B1DDD88AC00000000", transactionDatabaseManager));

        // BVT Invalid Send (Previous Input Is Invalid)
        // 19DE9FFBBBCFB68BED5810ADE0F9B0929DBEEB4A7AA1236021324267209BF478
        transactionIds.add(AddressProcessorTests.storeTransaction("0200000001C1887B3DD9E843530478B810B3E80E54984852A6F52A63EDD0D04A151E9E0325010000006A473044022044B04414FCEC21B9E811EE33A64B58A1781866229ED96679C7BF7076E89D560502202264C8AF69CEA244F16E6F4842F3D7D0CC6D7ACFF82FCE5FEEC26B0E370260A841210306CA102E0633D379AFA3DD3D90A6FDDDFA336B76827E5647D4CD516E705F2D40FFFFFFFF030000000000000000376A04534C500001010453454E442034DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED008000000001DCD651E22020000000000001976A91415E0A3C870799DE9823C3F4433A283A9731B1DDD88AC10310000000000001976A914395D58ECA9E61FBB078D2D813B2ED6B52337B76088AC00000000", transactionDatabaseManager));

        return transactionIds;
    }

    public static void assertTransactionSlpOutputs(final String transactionHash, final int[] slpOutputs, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();

        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(Sha256Hash.fromHexString(transactionHash));

        final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
        for (int i = 0; i < transactionOutputIds .getSize(); ++i) {
            final TransactionOutputId transactionOutputId = transactionOutputIds.get(i);

            final SlpTokenId slpTokenId = transactionOutputDatabaseManager.getSlpTokenId(transactionOutputId);
            if (Arrays.contains(slpOutputs, i)) {
                if (slpTokenId == null) {
                    Assert.fail(transactionHash + ":" + i + " was not marked as an SLP Output.");
                }
            }
            else {
                if (slpTokenId != null) {
                    Assert.fail(transactionHash + ":" + i + " was marked as an SLP Output.");
                }
            }
        }
    }

    @Test
    public void should_process_slp_transactions() throws Exception {
        final AddressProcessor addressProcessor = new AddressProcessor(_fullNodeDatabaseManagerFactory);
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            // Setup
            AddressProcessorTests.loadBvtTokens(databaseManager);

            // Action
            addressProcessor.start();

            final int maxSleepCount = 10;
            int sleepCount = 0;
            while (addressProcessor.getStatusMonitor().getStatus() != SleepyService.Status.SLEEPING) {
                Thread.sleep(250L);
                sleepCount += 1;

                if (sleepCount >= maxSleepCount) { throw new RuntimeException("Test execution timeout exceeded."); }
            }

            // Assert
            final FullNodeAddressDatabaseManager addressDatabaseManager = databaseManager.getAddressDatabaseManager();
            final List<TransactionId> slpTransactionIds = addressDatabaseManager.getSlpTransactionIds(SlpTokenId.fromHexString("34DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0"));
            Assert.assertEquals(14, slpTransactionIds.getSize());

            AddressProcessorTests.assertTransactionSlpOutputs("34DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0", new int[]{ 0, 1, 2 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("97BB8FFE6DC71AC5B263F322056069CF398CDA2677E21951364F00D2D572E887", new int[]{ 0, 1, 2 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("8572AA67141E5FB6C48557508D036542AAD99C828F22B429612BDCABBAD95373", new int[]{ 0, 1, 2 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("68092D36527D174CEA76797B3BB2677F61945FDECA01710976BF840664F7B71A", new int[]{ 0, 1 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("0F58E80BF3E747E32BCF3218D77DC01495622D723589D1F1D1FD98AEFA798D3D", new int[]{ 0, 1, 2 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("4C27492AA05C9D4248ADF3DA47A9915FB0694D00D01462FF48B461E36486DE99", new int[]{ 0, 1, 2, 3 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("87B17979CC05E9E5F5FA9E8C6D78482478A4E6F6D78360E818E16311F7F157F0", new int[]{ 0, 1, 2 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("731B7493DCAF21A368F384D75AD820F73F72DE9479622B35EF935E5D5C9D6F0E", new int[]{ 0, 1, 2 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("AE0D9AE505E4B75619A376FA70F7C295245F8FD28F3B625FBEA19E26AB29A928", new int[]{ 0, 1, 2 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("08937051BA961330600D382A749262753B8A941E9E155BA9798D2922C2CE3842", new int[]{ 0, 1 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("9DF13E226887F408207F94E99108706B55149AF8C8EB9D2F36427BA3007DCD64", new int[]{ 0, 1 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("25039E1E154AD0D0ED632AF5A6524898540EE8B310B878045343E8D93D7B88C1", new int[]{ 0, 1 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("19DE9FFBBBCFB68BED5810ADE0F9B0929DBEEB4A7AA1236021324267209BF478", new int[]{ 0, 1 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("9BD457D106B1EECBD43CD6ECA0A993420ABE16075B05012C8A76BB96D1AE16CE", new int[]{ 0, 1 }, databaseManager);
            AddressProcessorTests.assertTransactionSlpOutputs("16EA62D94AC142BAF93A6C44C5DC961883DC4D38B85F737ED5B7BB326707C647", new int[]{ }, databaseManager);
        }
        finally {
            addressProcessor.stop();
        }
    }
}
