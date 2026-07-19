rabbitmqadmin exchanges delete --name dev-opr8r-mo_sms-x
rabbitmqadmin exchanges delete --name dev-opr8r-mo_sms-xr
rabbitmqadmin exchanges delete --name dev-sndr-mt_sms-x
rabbitmqadmin exchanges delete --name dev-sndr-mt_sms-xr

rabbitmqadmin queues delete --name dev-opr8r-mo_sms
rabbitmqadmin queues delete --name dev-opr8r-mo_sms_fail
rabbitmqadmin queues delete --name dev-opr8r-mo_sms_retry_5s
rabbitmqadmin queues delete --name dev-sndr-mt_sms
rabbitmqadmin queues delete --name dev-sndr-mt_sms_fail
rabbitmqadmin queues delete --name dev-sndr-mt_sms_retry_5s

rabbitmqadmin exchanges delete --name dev-opr8r-mo_sms-x
rabbitmqadmin exchanges delete --name dev-opr8r-mo_sms-xr
rabbitmqadmin exchanges delete --name dev-sndr-mt_sms-x
rabbitmqadmin exchanges delete --name dev-sndr-mt_sms-xr

rabbitmqadmin queues list --columns name
rabbitmqadmin exchanges list