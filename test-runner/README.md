# Test Runner (RuneLite plugin)

Runs scripted tests in our test client against our own server and writes a pass/fail result per script, so a
repeatable check needs nobody watching the screen. A script says what to do (send `::tele`, walk to a tile, Talk-to an
NPC, continue a dialogue) and what should then be true (where the player stands, what the dialogue says, which chat
message arrived). Every step has a timeout.

It acts through RuneLite's client API: each click is the menu entry a real click would make, so the client sends the
same packets. Nothing is matched on pixels.

## Only our server, only test accounts

The plugin clicks for you. That is botting on Jagex's worlds, so:

- It is only ever sideloaded into the RSProx test client pointed at OSRSPS. It is not for the Plugin Hub or any other
  RuneLite.
- A script names its `account`, and only runs while that account is logged in. The account must also be in the
  plugin's **Test accounts** setting (default `test, test2`). Never add a real Jagex account there.

## Installing

RSProx starts its RuneLite in developer mode with the RuneLite folder `C:\RSPS\.rlcustom`, and developer mode loads every
jar in `sideloaded-plugins` at startup.

1. Download `test-runner-jar` from the latest `build` run on GitHub Actions and unzip it.
2. Copy `test-runner-<version>.jar` to `C:\RSPS\.rlcustom\sideloaded-plugins\` (remove an older test-runner jar).
3. Start `C:\RSPS\RSProx.cmd`. **OSRSPS Test Runner** is on by default; its folders are created on first start.

## Logging in

Nobody needs to type a password. While the client is on the login screen, the plugin logs in the **Auto login
account** (default `test`, empty turns it off) with the **Auto login password** (default `test`). The server's first login
claims a name with its password, and on 214 the test accounts are admins, so set a private password here (never a real
one) before anyone else can reach the server. The account must be one of the **Test accounts**. It
waits 3 s after the login screen shows, retries every 15 s, and stops after 5 tries in a row until a login succeeds or
the plugin restarts.

A second client on the same machine runs the plugin too. To log another account in on it, just start typing: a key
pressed on the login screen, a name other than the one auto login typed found in the login box (a logout since
included), or a login under another name tells the plugin a person is at that client, and auto login leaves it alone
until the plugin restarts.

So starting `C:\RSPS\RSProx.cmd` (or its scheduled task) is enough: the client logs in, runs every `runOnLogin`
script, and then any script dropped into the tests folder.

RSProx itself still waits for a click on **Launch Session**. [`launch\`](launch/README.md) has an `RSProx.cmd` that
clicks it, and `start-client.ps1`, which (re)starts the client and waits until it is logged in.

## Running a script

Scripts are `*.json` files in `C:\RSPS\.rlcustom\tests\`. A script runs:

- when it is copied into that folder or changed there (setting **Run dropped scripts**). Files already there when the
  client starts don't run; save one again to rerun it.
- after each login, when the script has `"runOnLogin": true` (setting **Run login scripts**).

Scripts run one at a time, in the order they were found. A script waits for a login before any step but
`waitForLogin`. The chatbox says when a script starts and how it ended.

To run many and get one table of results, `run-scripts.ps1` writes them into the tests folder one at a time, each once
the last one's result is in, and prints each script's result, steps passed, time and failure:

```powershell
.\test-runner\run-scripts.ps1 .\test-runner\examples          # every example, by file name
.\test-runner\run-scripts.ps1 .\test-runner\examples\castle-talk-cook.json .\test-runner\examples\hans-demo.json
```

It exits with 0 only when every script passed. Where Windows won't run scripts, start it with
`powershell -ExecutionPolicy Bypass -File .\test-runner\run-scripts.ps1 ...`, which allows it for that one run only.

```powershell
Copy-Item .\test-runner\examples\hans-demo.json C:\RSPS\.rlcustom\tests\
Get-Content C:\RSPS\.rlcustom\test-results\hans-demo.json
```

`hans-demo.json` first sends `::keys on`, so Hans says each line's key (`[hans.greeting]`) instead of the words in the
server's `data/dialogue.txt`, and the script passes whether or not that file has his words. Scripts in git check keys,
never words: the words are Jagex's and stay out of git (see the main README's Dialogue words).

`examples\` also has the Lumbridge Castle checks. They need runner 0.4.2 or later (`locOption` came in 0.3.0;
`takeItem`, `assertInventory`, `finishDialogue` and Examine in 0.4.0; `assertLoc` in 0.4.2). The combat checks
(`combat-attack-man.json`) need 0.5.0, which brought `assertHitsplat`, `assertNpcHealth`, `assertStat`,
`assertGroundItem` and `assertAnimation`; `combat-die-grave.json` needs 0.6.1 (`assertInterface`, and `clickWidget` from 0.6.0). `combat-harlan.json` needs 0.6.3, which reads and continues the two-item box (objectbox_double). `combat-raid-bows.json`
(the twisted bow, and the Venator bow's bounce in a zone `::multi` makes multi-way) needs 0.5.0. `combat-blowpipe.json` (charged with `::blowpipe`, shot, checked worn, then taken off and uncharged) needs 0.6.0 for `wornOption`. `combat-chinchompa.json` (black chinchompas thrown at a man, and their blast catching the man beside him in a `::multi` zone) needs 0.5.0. `combat-magic-strike.json` (Fire Strike from the spellbook on a man: its animation, graphics, runes and XP, the refusals for too low a level and too few runes, and a staff of fire standing in for the fire runes) needs 0.7.0 for `castOnNpc` and `assertSpotanim`. `combat-magic-autocast.json` (Wind Strike picked for autocast on the combat tab with a staff of fire, a man fought from one Attack click, defensive autocast's Defence XP, and a style button turning autocast off) needs 0.7.0 for `assertSpotanim`. `combat-magic-weakness.json` (the courtyard imp's 10% water weakness raising autocast Water Bolt's max hit from 10 to 11 in `::combat`, then the bolt cast on the imp) needs 0.7.0 for `assertSpotanim`. `combat-magic-bind.json` (Bind holding a man where he stands, a second Bind refused while he is held, and the immunity after, read off `::npcs`) needs 0.7.0 for `castOnNpc` and `assertSpotanim`. `combat-magic-ice.json` (Ice Barrage from the Ancient book, switched to with `::spellbook`, on one of two imps spawned side by side in a `::multi` zone, both frozen, read off `::npcs`) needs 0.7.0 for `castOnNpc` and `assertSpotanim`. `combat-magic-shadow.json` (Tumeken's shadow charged with `::shadow`, checked, wielded, its own spell cast on a man from one Attack click with a charge a cast and no runes, then taken off and uncharged for the runes back) needs 0.7.0 for `assertSpotanim`. `prayer-protect.json` (Protect from Melee clicked on in the prayer book, a man's punches blocked, its drain, Thick Skin alongside, the Piety refusal, and every prayer off at no points, read off `::prayers`) needs 0.6.0 for `clickWidget`. `prayer-boosts.json` (Piety clicked on raising a man fight's attack roll, max hit and defence roll in `::combat` from the next tick, and back once it is off) needs 0.6.0 for `clickWidget`. `prayer-redemption.json` (Redemption clicked on, a `::hit` leaving 9 of 99 hitpoints healed by 24, and every prayer point gone) needs 0.6.0 for `clickWidget`. `prayer-quick.json` (quick prayers: the prayer orb refused with nothing picked, Piety and Protect from Melee picked in its Setup, Done putting the prayer book back, and the orb turning both on and every prayer off, read off `::prayers` and `::varbit 4103`) needs 0.6.1 for `clickWidget` with `op` and `assertInterface`. `food-combo.json` (a shark and a karambwan eaten one after the other healing 38, then a meat pie in two bites leaving its dish, read off `::hp`) needs 0.6.0 for `itemOption`. `potions.json` (a super combat dose boosting the three stats by 19 and leaving 3 doses, a Saradomin brew healing past 99 and lowering Attack and Strength, a super restore bringing a drained Attack back, and a divine dose costing 10 hitpoints, read off `::stats` and `::hp` with `::keys on`) needs 0.6.0 for `itemOption`. `poison.json` (`::poison 20` hitting 4 thirty ticks on, an antipoison dose curing it and leaving 5 periods of immunity, venom getting through that immunity, and an anti-venom dose curing it and keeping venom off, read off `::poison` with `::keys on`) needs 0.6.0 for `itemOption`. `poison-npc.json` (`::poison npc 30` on a man spawned east of the castle, his 6 thirty ticks on read off `::npcs`, and the second 6 killing him with no XP) needs 0.5.0. `drain-npc.json` (`::drain defence 1` on a man spawned east of the castle, his defence roll in `::combat` falling from 430 to 387, read off `::npcs`, and the level back a minute on) needs 0.5.0. `spec-warhammer.json` (the spec orb lighting the bar, a dragon warhammer's Smash on a giant frog spawned east of the castle taking half the energy, its animation and graphic, the frog's Defence drained from 6 to 5 read off `::npcs`, and the combat tab's bar refused at 0%) needs 0.8.0 for `assertVarp` (and `assertSpotanim`). `spec-godsword.json` (the combat tab's bar lit, `::combat` showing Warstrike's doubled attack roll against slash, the godsword's animation and graphic on a giant frog spawned east of the castle, half the energy taken, and the frog's Defence drained by the damage, read off `::npcs`) needs 0.8.0 for `assertVarp` (and `assertSpotanim`). `spec-bulwark.json` (the combat tab's bar lit in a `::multi` zone east of the castle, a giant frog on 100 hitpoints attacked with Dinh's bulwark, and Shield Bash's animation and a hitsplat on a man beside it who was never attacked, for half the energy) needs 0.8.0 for `assertVarp`. `spec-bulwark-block.json` (the combat tab's Block button refusing an attack on a giant frog spawned east of the castle, read with `::keys on`, then Pummel hitting it once the 8 ticks off Block are up) needs 0.8.0 for `assertVarp`. `stun.json` (`::stun 9` showing the birds, a second stun refused, the stun over nine ticks on and the player walking again, then `::stun end`) needs 0.7.0 for `assertSpotanim`. `stats-restore.json` (a Strength boost losing a level and a Mining drain getting one back in a minute, Rapid Restore clicked on bringing the drain back twice as fast, a boost's 12-second segment kept over a `logout`, and Rapid Heal clicked on holding boosted Hitpoints a minute from the click, read off `::stats` and `::hp`) needs 0.6.0 for `clickWidget`.

- `lumbridge-castle-doors.json` (the smallest, run it first) and `castle-doors-rest.json`: every door.
- `lumbridge-castle-travel.json`, `castle-stairs-all.json` and `castle-ladders-all.json`: every option on the stairs,
  ladders, trapdoor and cellar ladder.
- `lumbridge-castle-search.json`, `castle-search-rest.json` and `castle-drawers.json`: every crate, sack, chest and
  drawers.
- `castle-talk-*.json`: Talk-to on each castle NPC. Like `hans-demo.json` they turn `::keys on` and expect each
  line's `[key]`, so they pass with or without words in `data/dialogue.txt`. The man's and woman's talks go a random
  way after the first line, so those two click through the rest with `finishDialogue`.
- `castle-examine.json`: Examine on every castle NPC and some locs, with `::keys on` (each answer is its
  `[examine....]` key).
- `castle-take-items.json`: empties the inventory, takes every castle item spawn and checks the inventory after each.
  An item taken less than 100 ticks before is not back yet, so each Take waits up to 70 s.

The equipment and ground item checks need 0.6.0 (`itemOption`, `wornOption`, `clickWidget`, `assertEquipment`,
`assertWidgetText`, `assertPose`, `logout`) and start from `::scenario lumbridge-bare`, ending with `::reset`:

- `equipment-wield.json`: Wield a bronze axe (worn, on the model, the combat tab says Axe), the stats screen's
  bonuses, Equip a shield from the stats screen, a two-handed sword taking the shield off, the whip's 70 Attack, the
  whip still worn after a logout, and Remove.
- `ground-drop-take.json`: drop an axe, see it on the ground, take it back.
- `lumbridge-bury-bones.json`: bury two bones, 4.5 Prayer XP each.
- `rotten-potato-eat.json`: the rotten potato's Eat menu: Set all stats to 50 one skill a tick, Heal me after a `::hit`, and Wipe inventory keeping the potato. Teleport to player and Spawn NPC ask for text (meslayer mode 9), which no runner step types yet; the headless tests cover them.
- `lumbridge-bank.json`: at a castle bank booth, deposit, withdraw 5 and as notes, deposit the inventory, log out and
  back in and take out what is still banked. It can't type an amount, so Withdraw-X isn't in it.
- `lumbridge-bank-tabs.json`: from `::scenario lumbridge-bank-tabs`, a tab with a placeholder that takes the logs back
  into their slot, Collapse tab, bank fillers and Clear-All's confirmation. The runner can't drag, so items aren't
  moved into tabs here.
- `lumbridge-deposit-box.json`: the castle's deposit box (Deposit-1, the 5 button, Wield from the extra option, a worn
  slot's Bank), then the bank's worn items view (its bonus lines, Wield, Remove and Deposit) and the banker's PIN
  settings screen.
- `lumbridge-shops.json`: the Lumbridge General Store (Value, Buy 1 and selling a pot back for nothing, the Quantity 5
  button, Sell 50) and Bob's Brilliant Axes (a pot refused, a bronze axe bought and sold back).
- `lumbridge-talk-shopkeepers.json`, `lumbridge-talk-sheared-ram.json`, `lumbridge-talk-donie-gee.json`,
  `lumbridge-talk-tutors.json`, `lumbridge-talk-townsfolk.json`: talk to each of the town centre's NPCs through every
  option with `::keys on`, checking each line's key (the Sheared Ram's needs 0.8.0 for `assertVarbit`).
- `lumbridge-freds-farm.json`: Fred the Farmer's talk (with `::keys on`), an onion picked from his patch, a sheep
  sheared for wool that the client then shows as sheared, and eight chickens killed for their feathers.
- `lumbridge-mill.json`: Millie Miller's and Gillie Groats' talks (with `::keys on`), wheat picked into grain, the
  grain up Mill Lane Mill's ladders into the hopper and through the controls into two pots of flour (checking
  varbits mill_flour and mill_showflour), Gillie's talk when milking with no bucket, and a dairy cow milked into two
  buckets. Needs 0.8.0 for `assertVarbit`.
- `lumbridge-ducks.json`: Examine (with `::keys on`) on a drake and a duck on the River Lum, the ducklings, the
  attackable duckling at the pond by Fred's farm and the duck and drake that walk on Groats' farm; Attack on a drake
  on the water says it can't be reached, and `::kill duck` shows the duck's death animation.
- `lumbridge-east.json`: east of the River Lum: Barfy Bill's talk (with `::keys on`) for a player below level 12
  Woodcutting, Examine on Bill, a giant spider and a goblin, and `::kill goblin` showing the goblin's death animation
  and leaving its bones, which are then taken.
- `lumbridge-swamp.json`: Lumbridge Swamp: Examine (with `::keys on`) on a level 6 giant rat, a frog, a big frog, a
  giant frog, the level 5 goblin and the unicorn; `::kill` on a giant rat (its death animation, then its raw rat meat
  taken) and a giant frog (its big bones taken); Father Urhney's spare ghostspeak amulet, given once; the four
  adventurers' talks; the candle seller refusing a player with no coins, then selling a lit candle for 1000; and the
  Mining and Fishing tutors' basics (with a pickaxe and a net carried), the Mining tutor's quests and the small net
  fish.
- `al-kharid-toll-gate.json`: Al Kharid step 1: Open on the toll gate from both sides with no coins, a Border
  Guard's talk (with `::keys on`) that ends with the player through the gate, and the palace's west large doors
  opened together and shut again, and the curtain upstairs in the palace opened and shut.
- `al-kharid-bank.json`: Al Kharid step 2: Bank on a booth (logs deposited), a banker's talk (with `::keys on`) that
  opens the bank (logs withdrawn), and a banker's Bank across the booth, with a relog between each to close the bank
  (an open bank hides the bankers from the mouse).
- `al-kharid-shops.json`: Al Kharid step 3: Trade with each of Al Kharid's seven shops (the General Store, Zeke,
  Dommik, Louie Legs, Ranael, the Gem trader and Ali Morrisane), buying one of the first item and selling it back,
  Ali's junk sold as his shop closes, then Louie Legs's talk (with `::keys on`) into his shop.
- `al-kharid-traders.json`: Al Kharid step 4: a kebab bought from Karim through his talk (with `::keys on`) and
  eaten, and silk haggled down to 2 gp from the Silk trader.
- `al-kharid-tanning.json`: Al Kharid step 4b: Ellis's talk (with `::keys on`) with cowhides carried, into the
  tanning screen; Tan 1, then Trade and Tan All, turning three cowhides into soft leather for a coin each.
- `al-kharid-palace.json`: Al Kharid step 5 (with `::keys on`): Chancellor Hassan's and Prince Ali's after-quest
  lines, a big window refused by Hassan, and a Keris bought from Osman for 20,000 coins.
- `al-kharid-fighters.json`: Al Kharid step 6 (with `::keys on`): Examine on an Al Kharid warrior, a man and a
  scorpion; a warrior killed for its bones and a scorpion killed, each put in on a quiet tile; Al the Camel's talk.
- `al-kharid-mine-fish.json`: Al Kharid step 7: gold and silver ore mined at 99 in the Al Kharid mine, shrimps netted
  at a Small Net spot by the sea, and the boxes at Ali Morrisane's stall searched (with `::keys on`).
- `draynor-village.json`: Draynor step 1: the pig pen's gate opened (both leaves) and shut, Morgan's stairs up and
  down with garlic from his cupboard, the Wise Old Man's stairs, Aggie's wardrobe and her cheese and tomato on the
  ground, and the bank opened from a banker's talk (`::keys on`) and from a booth. Needs 0.6.1 for `assertInterface`.
- `varrock-city.json`: Varrock step 1 (with `::keys on`): in through the south gate, a house's poor door opened and
  shut, a north-climbing and a south-climbing straight staircase up and down, a ladder, drawers opened, searched and
  shut, Pet on a stray dog and a man's talk.
- `varrock-banks.json`: Varrock step 2 (with `::keys on`): logs banked at a West Bank booth and taken back through a
  banker's talk, Knock-at on the West Bank's locked door, and the East Bank opened through a banker's Bank and a booth.
- `varrock-grand-exchange.json`: Varrock step 3 (with `::keys on`): a Grand Exchange clerk's Exchange, History and
  Talk-to (setting up offers), an exchange booth's Collect, a banker's Collect and a bank booth's Bank.
- `varrock-guards.json`: a Varrock guard and an Edgeville guard each put in on a quiet tile and killed, each dying with
  `human_death` and leaving bones.
- `draynor-willows.json`: Draynor step 2: the bronze axe taken off the logs by the willows and back 30 seconds later,
  a willow chopped at level 30, a shrimp netted at the shore's Small Net spots and cooked on the permanent fire.
- `draynor-market.json`: Draynor step 3: Fortunato's, Olivia's and Diango's talks (`::keys on`) into their shops, and
  one buy in each at the Wiki's price (a jug of wine for 1, a potato seed for 2, a Disk of Returning for 21,000, a Chronicle for 300), then a second Chronicle refused while one is held.
- `lumbridge-church.json`: Lumbridge church: Examine (with `::keys on`) on Father Aereck, the Prayer tutor, Nigel and
  Count Check; Pray-at the altar with drained prayer points (then again, full); Father Aereck's thanks, the Prayer
  tutor's two answers, Nigel's Deadman questions and his Trade, and Count Check's advice, who he is, the Salve, the
  Stronghold offer turned down and his check.
- `lumbridge-examine.json`: Examine (with `::keys on`) on scenery outside the castle: the church's altar, organ,
  pews and candles, and the graveyard's two gravestones.
- `lumbridge-walk.json`: Rest of Lumbridge step 12: walks from the castle courtyard to each area outside the castle in
  stages of at most 4 tiles (straight line), on the server's own pathing, teleporting back to the castle between three
  legs: the general store, Fred's farmhouse and Mill Lane Mill; Bob's axes, the church's west door, over the bridge
  and north to the Groats' farm; the swamp, its east and west mines and the adventurers' camp. Takes about six
  minutes.
- `lumbridge-take-items.json`: takes one item spawn in each area outside the castle (the general store's onion,
  Lozar's iron dagger, an egg and the shears at Fred's farm, the Groats' bucket, Father Urhney's leather gloves, swamp
  tar), teleporting onto each tile.
- `skills-levelup.json`: `::addxp` past a Woodcutting level, then an Attack level that raises the combat level: the
  chat message, the fireworks, the level-up box with each line's key (`::keys on`), and the combat level's box after
  the skill's. Walking closes each box. Needs only 0.7.0.
- `xp-rate.json`: `::xprate 2`, then a bone buried for 9 Prayer XP (4.5 at 1x), then `::xprate restore` back to the
  server's rate. It changes the rate for everyone on the server for a few seconds; a failure part way leaves it at 2x,
  so check `::xprate` after one. Needs only 0.5.0.
- `woodcutting-lumbridge.json`: Chop down with no axe, then a bronze axe on a tree north of the castle (one log, the
  stump, the tree back), then a level 99 dragon axe on the oak south of the castle (12 logs before its timer fells it).
  Needs only 0.5.0.
- `woodcutting-nests.json`: `::nest seeds` and `::nest ring` drop a nest next to the player as a log's 1/256 roll does;
  each is taken and searched, leaving an empty nest and the seed or ring. Needs only 0.6.0.
- `firemaking-after-chopping.json`: chops the oak south of the castle at 99 and lights the first oak logs straight
  away, while the woodcutting clock still runs; the tinderbox strikes (animation 733) and the attempt message shows at
  once, which the shared skill clock skipped before 30 Oct 2026. Needs 0.10.0.
- `firemaking-lumbridge.json`: at the spawn, a tinderbox on oak logs (level 15 needed), then on logs (lit after a
  roll or more, a step west, 40 XP), Light on logs on the ground at level 43, and the first fire's ashes. Needs 0.10.0
  (`useItemOnItem`, `groundItemOption`).
- `firemaking-picked-crop.json`: picks an onion in Fred's patch, stands on its tile and uses a tinderbox on logs; the
  fire is refused, the logs stay in the backpack and the onion grows back (third exploit review). Needs 0.10.0.
- `mining-lumbridge.json`: at the Lumbridge Swamp mine, Mine with no pickaxe, then a bronze pickaxe on copper (one
  ore, the empty rock, the copper back) and tin, and mithril's level 55. Needs only 0.5.0.
- `fishing-lumbridge.json`: at the Lumbridge Swamp, Net with no net, then a net nets a shrimp (10 XP); Bait's
  level 5, and on the River Lum, Lure's level 20. Needs only 0.5.0.
- `cooking-lumbridge.json`: in the Lumbridge Castle kitchen, Cook on the range with nothing raw, then at 99 the Make-X
  box over three raw shrimps cooks all three (90 XP).
- `smithing-lumbridge.json`: north of Lumbridge Castle, the furnace's Make-X box smelts a bronze bar, then the rusted
  anvil needs a hammer and hammers the bar into a dagger. Needs 0.8.0 (`assertVarbit`).
- `fletching-logs.json`: a knife on three logs at 99 cuts three shortbows (u) through the Make-X box (15 XP), then at
  level 1 a longbow needs 10. Needs 0.10.0 (`useItemOnItem`).
- `fletching-arrows.json`: at 99, feathers on 20 arrow shafts make headless arrows in sets (15, then 5), bronze
  arrowtips make 15 bronze arrows, a bow string strings a shortbow and a feather on dart tips makes 10 darts. Needs
  0.10.0.
- `crafting-spinning.json`: at Lumbridge Castle's first-floor spinning wheel, Spin with nothing does nothing, then at
  99 two wool spin into balls (5 XP), and at level 1 flax needs 10. Needs only 0.5.0.
- `crafting-leather.json`: at 99, a needle on leather makes two pairs of gloves with one thread (one left), steel
  studs stud a leather body, and at level 1 green d'hide chaps need 60. Needs 0.10.0.
- `crafting-gems.json`: at 99 a chisel cuts two sapphires (100 XP); at level 1 a sapphire needs 20 and three uncut
  opals are all cut or crushed. Needs 0.10.0.
- `crafting-jewellery.json`: at the furnace north of Lumbridge Castle, a gold bar opens `crafting_gold` and All makes
  two sapphire rings, a tiara mould opens `silver_crafting` and makes a tiara, and a ball of wool strings a gold
  amulet. Needs 0.15.0 (`useItemOnLoc`).
- `crafting-glass.json`: at the furnace north of Lumbridge Castle, soda ash and sand make molten glass (the buckets
  back), a pipe blows two vials, and water softens clay; at Barbarian Village's potter's wheel one soft clay shapes four
  unfired cups, which the oven fires. Needs 0.15.0.
- `herblore-basics.json`: at level 1 a grimy guam needs 3; at 99 two clean (5 XP), go into vials of water as
  unfinished potions, a pestle grinds two unicorn horns, and two vials fill at Lumbridge Castle's kitchen sink. Needs
  0.15.0 (`useItemOnLoc`).
- `herblore-potions.json`: at level 1 an attack potion needs 3; at 99 eye of newt on two guam potions (unf) makes
  two attack potions(3) (50 XP), and unicorn horn dust on a marrentill potion (unf) makes an antipoison. Needs 0.10.0.
- `ground-high-id-take.json`: an item with an id above 32767 (rosewood logs, 32910) goes from the ground when taken,
  which tells whether the client keeps all 16 bits of the id in OBJ_DEL. Needs only 0.5.0.

The settings checks need 0.8.0 (`assertVarp`, `assertVarbit`, `mouseClick`). They set each setting
to a known start with `::varp` and `::varbit` and put the account back at the end:

- `settings-side.json`: the settings side panel's three tabs, scroll-wheel zoom, accept aid, skull prevention, run,
  the player attack priority dropdown, the master and music sliders (a mouse click in the middle of each, 58%) and the
  master mute; every one checked in both the server's copy and the client's, then again after a relog.
- `settings-toggles.json`: the All Settings window from the side panel's button; a toggle in each category but Popout
  (clicked by its clickzone child, the row's place in its category), the wiki button refused while the data orbs are
  off, and a search (its text set with `runClientScript` 5968), where children count across every category; then
  close, relog and check every one.
- `settings-dropdowns.json`: dropdowns and F-key binds in the All Settings window, each picked from its list (the
  row's clickzone child, then child 3n + 2 of dropdown_buttons for option n) and checked once the client has read it
  again: attack priority, the combat and prayer tab keys (a key taken from the tab that had it), login/logout
  notifications and quest list sort; and the master and music sliders (a mouse click in the middle of each one's
  clickzone, a settings_content child, 53%); then close, relog and check every one.
- `social-friends-test.json` and `social-friends-test2.json` (need 0.13.0): two players at once, `test` on one VM
  and `test2` on the other, started together. Each empties its friends list (`::clearfriends`), logs out and in, and
  adds the other; `test` sees `test2` come online on world 301, go offline at `test2`'s logout and come back. Run alone,
  each waits up to 3 minutes for the other and then fails.
- `social-pm-test.json` and `social-pm-test2.json` (need 0.14.0): two players at once like the friends pair; each adds the
  other, `test` messages `test2` and `test2` answers.
- `social-trade-offer-test.json` and `social-trade-offer-test2.json` (need 0.16.0): two players at once; `test` offers
  10 coins, sees `test2`'s bronze axe on offer, takes 5 coins back and declines, and both get their items back. Each
  starts from `lumbridge-bare` and ends with `::reset`.
- `social-trade-request-test.json` and `social-trade-request-test2.json` (need 0.16.0): two players at once; `test` asks
  `test2` to trade, `test2` accepts, both trade screens open and `test` declines.
- `social-trade-test.json` and `social-trade-test2.json` (need 0.16.0): two players at once; `test` offers 10 coins for
  `test2`'s bronze axe, both accept on both screens and the items swap. Each starts from `lumbridge-bare` and ends with
  `::reset`.
- `social-friends-chat-test.json` and `social-friends-chat-test2.json` (need 0.18.0): two players at once; `test` names
  its friends chat channel through Setup and joins it, `test2` joins by `test`'s name, each sees the other's `/` line,
  and both leave.
- `social-ignore.json` (needs 0.14.0): `test` ignores `test2` through the ignore tab's prompt and deletes the name again; the ignore tab's friends button goes back to the Friends tab.
- `social-ignore-2.json` (needs 0.14.0): the same with the names swapped, for a client logged in as `test2` (the nightly run's account).
- `settings-inputs.json` (needs 0.11.0): the loot notification and Alchemy warning values typed into the server's
  number prompt with `enterAmount`, public chat's colour picked in the colour picker and saved, then a cancelled pick,
  and the public chat filter set to friends; then close, relog and check every one, the filter by its label.
- `settings-buttons.json`: the All Settings window's buttons. The Wilderness teleport and tablet warnings switched on
  and off a section at a time, the opaque chat colours reset, the keybinds restored and the volumes reset; then close,
  relog and check, and the keys put back to none.
- `settings-search.json` (needs 0.12.0): the All Settings search typed with real key presses, a found row clicked,
  and a window closed mid-search reopening on its category, also after a relog.
- `settings-sweep-0.json` to `settings-sweep-7.json`: one category each, Activities to Warnings. Every toggle in
  it with no dependency, guard or var shared with another row is set to 0, clicked on and checked, then checked again
  after a relog and put back to 0. Generated from the 241 cache's settings catalog; run them one at a time.

## Results

Results go to `C:\RSPS\.rlcustom\test-results\`:

- `<script>.json`: the last run, replaced by each run (written whole, then moved into place).
- `<script>.png`: the client's picture when that run failed (setting **Screenshot on failure**). A passing run deletes
  it, so a png is always the picture of the result next to it.
- `history\<UTC time>_<script>.json`: every run (time to the millisecond).

`<script>` is the file name without `.json`, with any character but letters, digits, `.`, `_` and `-` written as `%XX` (so `my test.json` gives `my%20test`). If a failure's screenshot comes in after a newer run of the same script has finished, the older result only goes to `history`.

```json
{
  "script": "hans-demo",
  "name": "Teleport, walk and talk to Hans",
  "result": "fail",
  "failure": "step 7 (assert dialogue from Hans contains \"[hans.greeting]\"): timed out after 20000 ms: no dialogue open",
  "startedAt": "2026-10-24T20:00:00Z",
  "ms": 23140,
  "ticks": 38,
  "trigger": "file",
  "runner": "0.1.0",
  "screenshot": "hans-demo.png",
  "steps": [
    {"n": 1, "step": "waitForLogin", "does": "wait for login", "result": "pass", "detail": "logged in as test", "ms": 0, "ticks": 0},
    {"n": 7, "step": "assertDialogue", "does": "...", "result": "fail", "detail": "timed out after 20000 ms: no dialogue open", "ms": 20000, "ticks": 33},
    {"n": 8, "step": "continueDialogue", "does": "continue dialogue", "result": "skipped"}
  ]
}
```

`result` is `pass`, `fail`, or `error` when the file could not be read as a script (then `failure` says why and no step
ran). `ms` and `ticks` are wall time and game ticks seen; after a failing step the rest are `skipped`.

## Script format

```json
{
  "name": "Teleport, walk and talk to Hans",
  "account": "test",
  "runOnLogin": false,
  "timeoutMs": 10000,
  "steps": [
    {"step": "waitForLogin"},
    {"step": "command", "text": "::tele 3222 3218", "expectMessage": "Teleported to 3222, 3218"},
    {"step": "assertLocation", "x": 3222, "y": 3218, "plane": 0}
  ]
}
```

`account` and `steps` are required. `timeoutMs` (default 10000) is the timeout of every step that doesn't set its own.
Any object may carry a `comment`. Unknown fields are an error, so a typo can't be silently ignored. Tiles are
`x`, `y`, `plane` as RuneLite shows them (`y` is our server's `z`).

The runner polls the current step every client cycle (20 ms). A step that acts does so once, as soon as it can, and
then waits to see it worked; an assert passes as soon as it holds. So an assert right after an action waits for the
server's answer instead of failing on what was there before.

| `step` | Fields | Does |
| --- | --- | --- |
| `waitForLogin` | | Waits until a player is logged in. Default timeout 120 s. |
| `command` | `text`, `expectMessage` | Puts `text` in the chat input and presses Enter. `::` is added if missing. With `expectMessage`, waits for a chat message containing it. |
| `chat` | `text`, `expectMessage` | Same, without adding `::`. |
| `assertLocation` | `x`, `y`, `plane` | Waits until the player stands on the tile, as the server has it and as the client draws it. Without `plane`, any plane. If the client draws the player on another tile, the step says where. |
| `walk` | `x`, `y` | Moves the mouse over the tile and, once the client has drawn a frame with it there, clicks the Walk here the client offers at that spot; then waits until the player stands on it. The tile must be on screen; until it is (right after a teleport the scene can lag a moment), it keeps trying, turning the camera toward the tile and tilting it down. If the player stops on another tile for 3 game ticks, it clicks again, and while they head for another tile the step says which. It doesn't click while the client draws the standing player on another tile than the server has them on, since the camera follows the drawn player; and it passes once the player is drawn on the tile. A tile that stays off screen is reported with the camera's angles and position. Default timeout 30 s. |
| `npcOption` | `npc` (name) or `npcId`, `option` | Clicks the option (such as `Talk-to`, or `Examine`, which every NPC has) on the nearest such NPC, retrying until one is in view. An NPC on a long walk can be out of view for a while (Hans's lap of the castle takes about a minute): give its step a `timeoutMs` that covers the walk. Doesn't wait for the result: follow it with an assert. |
| `locOption` | `loc` (name) or `locId`, `option`, `x`+`y` | Clicks the option (such as `Open`, `Climb-up`, `Search`, or `Examine`, which every loc has) on the nearest such loc on the player's plane, retrying until one is in the scene. With `x` and `y`, only a loc on that tile (a big loc on any tile it covers). A loc that changes with a varbit is matched, and examined, by what it is now. Doesn't wait for the result: follow it with an assert. |
| `assertLoc` | `loc` (name) or `locId`, `x`, `y`, `option` | Waits until such a loc is on the tile on the player's plane (a big loc on any tile it covers), with `option` when given: a door's `Open` once it has shut. Put one after a last Close before a teleport: a teleport cancels a click the server hasn't carried out yet, and the door would stay open for the next run. |
| `takeItem` | `item` (name) or `itemId`, `x`+`y` | Clicks Take on the nearest such item on the ground on the player's plane, retrying until one is in the scene. With `x` and `y`, only an item on that tile. Take is the third ground option, as the server has it. Doesn't wait for the result: follow it with `assertInventory`. |
| `assertInventory` | `item` (name) or `itemId`, `count` | Waits until the inventory holds exactly `count` of the item (all stacks added up), or any at all without `count`. `count` 0 waits until there are none. |
| `assertDialogue` | `contains`, `speaker` | Waits until an open dialogue's text contains `contains` and its name is `speaker` (both optional). Colour tags and line breaks are removed first. |
| `continueDialogue` | | Waits for a dialogue with Click here to continue, clicks it, and waits until that dialogue has changed or closed. (0.17.0) The level-up box counts too. |
| `chooseOption` | `text` | Waits for an option menu, clicks the option equal to `text` (else the first containing it), and waits until the menu has gone. |
| `assertNoDialogue` | | Waits until no dialogue is open. |
| `finishDialogue` | | Clicks through whatever dialogue is open until none is: continue on each page, the first option on a menu. Passes at once when none is open. For talks that go a random way. (0.17.0) Clicks a level-up box away too; `assertDialogue` and `assertNoDialogue` still ignore it. |
| `assertMessage` | `contains` | Waits for a chat message containing `contains`, counting only messages since the last step that acted. |
| `waitTicks` | `ticks` | Waits that many game ticks. |
| `assertHitsplat` | `npc` (name) or `self: true`, `amount` | Waits for a hitsplat on the NPC of that name (the one the player is fighting or talking to, else the nearest; several castle NPCs are a `Man`), or on the player, counting only hitsplats since the last step that acted. With `amount`, one showing exactly that; a miss shows 0. |
| `assertNpcHealth` | `npc` (name) or `npcId`, `min`, `max` | Waits until that NPC's health bar (the NPC chosen as for `assertHitsplat`) is within the bounds, in percent (`max: 99` is "hurt"). The client is only told an NPC's health once it is hit, so this also waits for the first hit to show, and says `no health bar` until then. |
| `assertStat` | `skill`, `level`, `xp` | Waits until the player's real (unboosted) level and experience in the skill are at least those given. `skill` is a name as the stats tab shows it, such as `Attack`. |
| `assertGroundItem` | `item` (name) or `itemId`, `x`+`y`, `absent` | Waits until such an item lies on the ground in the scene on the player's plane (on the tile, with `x` and `y`), or, with `absent: true`, until none does; absence on a tile counts only once that tile is in the loaded scene. |
| `assertAnimation` | `id`, `npc` (name) | Waits until the player, or the NPC of that name (chosen as for `assertHitsplat`), has started animation `id` (a cache sequence id, such as 422 for a punch), counting only animations since the last step that acted. |
| `assertSpotanim` | `id` or `ids`, `npc` (name) | (0.7.0) Waits until the player, or the NPC of that name (chosen as for `assertHitsplat`), has been given spotanim `id` (a cache spotanim id, such as 101 for Fire Strike's impact), or any of the list `ids`, counting only spotanims since the last step that acted. The client doesn't say which spotanim changed, so one the actor already had counts again when it gets another. |
| `castOnNpc` | `interfaceId`, `component`, `npc` (name) or `npcId` | (0.7.0) Picks the spell at that component (the standard spellbook is 218; Fire Strike is 218:19) and casts it on the NPC, chosen as for `assertHitsplat`, as clicking the spell and then Cast on the NPC would; retries until the interface is loaded and the NPC in view. Doesn't wait for the result. |
| `itemOption` | `item` (name) or `itemId`, `option` | Clicks the option (such as `Wield`, `Wear`, `Eat`, `Drop`, or `Examine`) on the first such item in the inventory, retrying until there is one. Doesn't wait for the result: follow it with an assert. |
| `wornOption` | `slot`, `option` | Clicks the option (`Remove`, `Examine`, or one of the item's own) on a slot of the equipment tab. `slot` is one of `head`, `cape`, `neck`, `weapon`, `body`, `shield`, `legs`, `hands`, `feet`, `ring`, `ammo`. |
| `clickWidget` | `interfaceId`, `component`, `slot`, `op` | Clicks op `op` (default 1) on an interface component, or on its slot `slot` (an item in an interface's inventory), as that menu entry would; retries until the interface is open. The equipment tab's View equipment stats is 387:1. |
| `assertEquipment` | `slot`, `item` (name) or `itemId`, `empty` | Waits until the slot of the worn inventory holds the item, or with `empty: true` nothing, and the player's model shows the same (rings and ammo aren't on the model). |
| `assertWidgetText` | `interfaceId`, `component`, `contains` | Waits until the component's text, with everything inside it, contains `contains`; without `component`, any text in the interface. Colour tags are removed first. The interface must be open. Text the server sets (IF_SETTEXT) is there on a side tab that isn't showing, but text a tab's own client scripts write (the combat tab's style buttons) only follows changes while that tab shows: open it first with `clickWidget` on its stone (fixed frame 548, stone0 64 for combat). |
| `assertPose` | `idle` | Waits until the player stands in that animation: the ready pose of their weapon's stance (808 is human_ready). |
| `logout` | | Clicks Click here to logout and waits until the client is logged out. Auto login logs the account back in; follow it with `waitForLogin`. |
| `assertInterface` | `interfaceId`, `absent` | Waits until the interface (by id, such as 672 for the gravestone) is open, or, with `absent: true`, until it is not. |
| `assertVarp` | `id`, `value` | (0.8.0) Waits until varp `id` holds `value` both as the server last sent it and in the client's own copy. The client's scripts change their copy first for most settings, so a setting the server ignored shows as the client alone holding it; the result shows both. |
| `assertVarbit` | `id`, `value` | (0.8.0) The same for varbit `id`, worked out from both copies of its varp with the client's varbit definition. |
| `runClientScript` | `id`, `args` | (0.8.0) Runs client script `id` from the cache with `args` (whole numbers and strings in the script's order; a component is interface × 65536 + component), as the client's interfaces would. For what the runner can't do by clicking. A script's `if_runscript` sends nothing when run this way, so a slider needs a `mouseClick` instead (VM, 2026-10-29). |
| `assertWeight` | `kg` | (0.9.0) Waits until the carried weight the server last sent is `kg` whole kilograms (rounded toward zero, so -4.535 kg is -4), as RuneLite's weight shows it. |
| `groundItemOption` | `item` (name) or `itemId`, `op`, `option`, `x`+`y` | (0.10.0) Clicks ground op `op` (1 to 5; Take is 3, logs' Light is 4) on the nearest such item on the ground, as `takeItem` does Take; `option` is the menu text. RuneLite doesn't give an item's ground options, so the script names the op. |
| `useItemOnItem` | `item` (name) or `itemId`, `onItem` or `onItemId` | (0.10.0) Clicks Use on the first such item in the inventory, then the first other slot holding the second item, as the server reads it (IF_BUTTONT). Retries until both are there; doesn't wait for the result. |
| `useItemOnLoc` | `item` (name) or `itemId`, `loc` (name) or `locId`, `x`+`y` | (0.15.0) Clicks Use on the first such item in the inventory, then the nearest such loc (on that tile, when given), as the server reads it (OPLOCT). Retries until both are there; doesn't wait for the result. |
| `enterAmount` | `amount` | (0.11.0) Waits for the chatbox's number prompt (meslayer mode 7, as after Withdraw-X or a settings number), puts the amount in it and presses Enter, then waits until the prompt has closed. |
| `addFriend`, `deleteFriend` | `name` | (0.13.0) Clicks the Friends tab's Add Friend (or Delete Friend) button, waits for its chatbox prompt, types the name and presses Enter, as a player does; the client's own scripts then check it and tell the server. Waits for the list to arrive first and for the prompt to close after. Doesn't wait for the server's answer; follow it with `assertFriend`. |
| `assertFriend` | `name`, `world` or `absent` | (0.13.0) Waits until the friends list shows the name (case and spaces against underscores don't matter) on world `world`, 0 for offline; or, with `absent: true`, until the name isn't on it. The result lists the whole list. |
| `typeText` | `text` | (0.12.0) Types the text as key presses, one character at a time (press, typed, release on the client's canvas), for what reads the keyboard itself, such as the All Settings search box after its Search. Passes a game tick after the keys reach the canvas, so the client has taken them before the next step; check their effect with that step. |
| `addIgnore`, `deleteIgnore` | `name` | (0.14.0) The same through the ignore tab's Add Name and Delete Name buttons (prompts 4 and 5), opening the ignore tab from the Friends tab first when it isn't open. |
| `assertIgnore` | `name`, `absent` | (0.14.0) Waits until the ignore list holds the name, or, with `absent: true`, doesn't. |
| `privateMessage` | `name`, `text` | (0.14.0) Opens the private message prompt for `name` (client script 107, meslayer_mode6, which a friend's Message option runs), types `text` and presses Enter. Check delivery with `assertMessage` on the other client. |
| `playerOption` | `player`, `option`, `x`+`y` | (0.16.0) Clicks an option on the other player with that name, as their right-click menu does ("Trade with", "Follow"); retries until they are in view, and with `x` and `y` until they stand on that tile (a two-VM script's sign that the other side has finished its setup). A trade request's Accept trade is Trade with on its sender, so this accepts one too. |
| `setFriendsChatName` | `name` | (0.18.0) Opens the friends chat Setup screen (its button on the Chat-channel tab's friends chat panel) if it isn't open, picks Set prefix, and types `name` into the server's text prompt. Check it with `assertWidgetText` on 94:10. |
| `joinFriendsChat` | `owner` | (0.18.0) Opens Join Chat's prompt (client script 111, meslayer_mode10, which the Join Chat button runs) and types the owner's name. Leave Chat is `clickWidget` on 7:18. Talk with `chat` and a `/` in front. |
| `assertFriendsChat` | `owner`, `member`, `absent` | (0.18.0) Passes when the client is in `owner`'s friends chat, with `member` listed when given (or, with `absent`, not listed). `absent` alone passes when it is in no channel. |
| `mouseClick` | `interfaceId`, `component`, `slot` | (0.8.0) Presses the left mouse button on the middle of the component (or its slot), for what opens on the mouse click itself rather than on a menu option, such as a dropdown's box. Assumes stretched mode is off. |

Dialogues are the chatbox's chat_left (NPC), chat_right (player), chatmenu (options), messagebox, objectbox and (0.6.3)
objectbox_double, the box with two items.

## Known limits

- Chat is sent by putting the text in the chat input and sending an Enter key to the client's canvas. If a key
  remapping plugin is on, it may swallow the Enter. When the client leaves the line in the input, the runner asks for
  the canvas's keyboard focus and presses Enter again, up to five times two ticks apart (0.5.1); the result says
  whether the canvas had focus. (0.17.0) An open chatbox prompt takes the Enter for itself, so one of those tries
  closes it instead (once per line), and the result also says whether the Swing thread had sent the last Enter,
  where the keyboard focus was, whether the window was active and any prompt still open. If the line still stays,
  restart the client and keep that result: its detail is what will find the cause.
- `walk` needs the tile on screen: it clicks the 3D view, as a player does. Walk in stages for far tiles. When the tile
  is off screen it turns the camera toward it, so a script can leave the camera however it finds it.
- Auto login starts the login by setting the client's game state to LOGGING_IN, as RuneLite's developer tools do.
  If it doesn't log in, the plugin log (`C:\RSPS\.runelite\logs\client.log`) says what it tried.

## Building

`./gradlew -p test-runner build` (CI's `test-runner` job). The tests run without a client: steps, the runner, script
parsing and the result files are plain Java, behind the `Game` interface that `ClientGame` implements over RuneLite.
