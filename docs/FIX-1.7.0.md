# Direct tool and block supply — 1.7.0

The crafting-table workflow has been removed from the active mod path. There is no `/bot craft` command, and mining, navigation and construction do not open tables, execute recipes or use furnaces.

When a mining task lacks a usable harvest tool, it receives the minimum valid tier directly through the server-authoritative inventory grant path: wooden, stone, iron, diamond or netherite as required by the block's harvest rules. Existing usable tools are reused.

Build plans and navigation gaps receive common cobblestone when no safe disposable construction block is present. Grants are capacity checked, preserve held equipment and are consumed by normal placement. Guard-area starter dirt remains a separate one-time supply.

The command parser rejects `/bot craft ...` and `/bot all craft ...`. The full Gradle test suite passes after the change; live-world interaction still requires the manual scenarios in `TESTING.md`.
