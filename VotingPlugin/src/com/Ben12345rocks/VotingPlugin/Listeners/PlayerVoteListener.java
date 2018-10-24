package com.Ben12345rocks.VotingPlugin.Listeners;

import java.util.ArrayList;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.UserManager.UserStorage;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.VotingPlugin.Main;
import com.Ben12345rocks.VotingPlugin.Config.Config;
import com.Ben12345rocks.VotingPlugin.Config.ConfigVoteSites;
import com.Ben12345rocks.VotingPlugin.Events.PlayerVoteEvent;
import com.Ben12345rocks.VotingPlugin.Objects.User;
import com.Ben12345rocks.VotingPlugin.Objects.VoteSite;
import com.Ben12345rocks.VotingPlugin.SpecialRewards.SpecialRewards;
import com.Ben12345rocks.VotingPlugin.UserManager.UserManager;
import com.Ben12345rocks.VotingPlugin.VoteParty.VoteParty;

// TODO: Auto-generated Javadoc
/**
 * The Class VotiferEvent.
 */
public class PlayerVoteListener implements Listener {

	/** The config. */
	static Config config = Config.getInstance();

	/** The config vote sites. */
	static ConfigVoteSites configVoteSites = ConfigVoteSites.getInstance();

	/** The plugin. */
	static Main plugin = Main.plugin;

	private static Object object = new Object();

	/**
	 * Instantiates a new votifer event.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public PlayerVoteListener(Main plugin) {
		PlayerVoteListener.plugin = plugin;
	}

	/**
	 * On votifer event.
	 *
	 * @param event
	 *            the event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onplayerVote(PlayerVoteEvent event) {
		String playerName = event.getPlayer();
		if (!PlayerUtils.getInstance().isValidUser(playerName)) {
			if (!config.allowUnJoined()) {
				plugin.getLogger().warning("Player " + playerName
						+ " has not joined before, disregarding vote, set AllowUnjoined to true to prevent this");
				return;
			}
		}

		VoteSite voteSite = event.getVoteSite();

		// check valid service sites
		if (voteSite == null) {
			if (!Config.getInstance().getDisableNoServiceSiteMessage()) {
				plugin.getLogger().warning("No voting site with the service site: '" + event.getServiceSite() + "'");
				plugin.getLogger().warning(
						"Please read here on how to fix it: https://github.com/Ben12345rocks/VotingPlugin/wiki/Common-Problems");

				ArrayList<String> services = new ArrayList<String>();
				for (VoteSite site : plugin.getVoteSites()) {
					services.add(site.getServiceSite());
				}
				plugin.getLogger()
						.warning("Current known service sites: " + ArrayUtils.getInstance().makeStringList(services));
			}
			return;
		}

		User user = UserManager.getInstance().getVotingPluginUser(playerName);

		synchronized (object) {

			// vote party
			VoteParty.getInstance().vote(user, event.isRealVote());

			// broadcast vote if enabled in config
			if (config.getBroadCastVotesEnabled()) {
				if (!Config.getInstance().getFormatBroadcastWhenOnline() || user.isOnline()) {
					voteSite.broadcastVote(user);
				}
			}

			// update last vote time
			user.setTime(voteSite);

			// check first vote rewards
			SpecialRewards.getInstance().checkFirstVote(user);

			if (user.isReminded()) {
				user.setReminded(false);
			}

			// check if player has voted on all sites in one day

			if ((user.isOnline() || voteSite.isGiveOffline()) && AdvancedCoreHook.getInstance().getOptions().isProcessRewards()) {
				user.playerVote(voteSite, true, false);
				user.closeInv();
			} else {
				user.addOfflineVote(voteSite.getKey());
				plugin.debug(
						"Offline vote set for " + playerName + " (" + user.getUUID() + ") on " + voteSite.getKey());
			}

			// add to total votes
			if (Config.getInstance().getCountFakeVotes() || event.isRealVote()) {
				if (Config.getInstance().getAddTotals()) {
					user.addTotal();
					user.addTotalDaily();
					user.addTotalWeekly();
				}
				user.addPoints();
			}

			// other rewards
			SpecialRewards.getInstance().checkAllSites(user);
			SpecialRewards.getInstance().checkCumualativeVotes(user);
			SpecialRewards.getInstance().checkMilestone(user);

			if (Config.getInstance().getClearCacheOnVote()) {
				if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
					AdvancedCoreHook.getInstance().getMysql().removePlayer(user.getUUID());
				}
			}
		}

		plugin.setUpdate(true);
	}

}