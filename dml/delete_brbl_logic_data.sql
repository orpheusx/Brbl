BEGIN TRANSACTION ;
delete from brbl_logic.campaign_users;
delete from brbl_logic.push_campaigns;
delete from brbl_logic.scripts;
delete from brbl_logic.routes;
COMMIT ;