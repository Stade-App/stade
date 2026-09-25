import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;

public final class UpdateSigner {

    private static final String PRIVATE_KEY_FILE = "update-key.hex";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        switch (args[0]) {
            case "keygen" -> keygen();
            case "sign" -> {
                if (args.length < 2) {
                    usage();
                    System.exit(2);
                }
                sign(Paths.get(args[1]));
            }
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    private static void usage() {
        System.err.println("usage: UpdateSigner keygen");
        System.err.println("       UpdateSigner sign <file>   (reads UPDATE_SIGNING_KEY, writes <file>.sig)");
    }

    private static void keygen() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();

        byte[] publicRaw = tail32(pair.getPublic().getEncoded());
        byte[] privateRaw = tail32(pair.getPrivate().getEncoded());

        Path out = Paths.get(PRIVATE_KEY_FILE);
        Files.writeString(out, toHex(privateRaw), StandardCharsets.UTF_8);
        restrict(out);

        System.out.println("public key (paste into UPDATE_SIGNING_PUBLIC_KEY):");
        System.out.println(toHex(publicRaw));
        System.out.println();
        System.out.println("private key written to " + out.toAbsolutePath());
        System.out.println("store it as the UPDATE_SIGNING_KEY secret, then delete the file");
    }

    private static void sign(Path file) throws Exception {
        String keyHex = System.getenv("UPDATE_SIGNING_KEY");
        if (keyHex == null || keyHex.isBlank()) {
            System.err.println("UPDATE_SIGNING_KEY is not set");
            System.exit(1);
        }
        byte[] seed = fromHex(keyHex.trim());
        if (seed.length != 32) {
            System.err.println("UPDATE_SIGNING_KEY must be 32 bytes of hex");
            System.exit(1);
        }

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));

        KeyFactory factory = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = factory.generatePrivate(
            new EdECPrivateKeySpec(NamedParameterSpec.ED25519, seed)
        );
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(digest);
        byte[] signature = signer.sign();

        Path out = Paths.get(file.toString() + ".sig");
        Files.writeString(out, toHex(signature), StandardCharsets.UTF_8);
        System.out.println("signed " + file.getFileName() + " -> " + out.getFileName());
    }

    private static byte[] tail32(byte[] encoded) {
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return raw;
    }

    private static void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException ignored) {
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
