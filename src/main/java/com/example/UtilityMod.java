 import net.fabricmc.api.ModInitializer;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;

    public class UtilityMod implements ModInitializer {
        public static final String MOD_ID = "snowstormxd_utility_mod";
        public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

        @Override
        public void onInitialize() {
            LOGGER.info("Hello from {}!", MOD_ID);
        }
    }
    ```
