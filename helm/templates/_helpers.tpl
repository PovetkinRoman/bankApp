{{/*
Define shared exposure routes for Gateway API and Ingress resources.
Returns a JSON array so it can be parsed with fromJson in templates.
*/}}
{{- define "bankapp.exposureRoutes" -}}
{{- $releaseName := .Release.Name -}}
{{- $routes := list -}}
{{- $routes = append $routes (dict
    "name" "front-ui"
    "serviceName" (printf "%s-front-ui" $releaseName)
    "port" (index .Values "front-ui" "service" "port")
    "paths" (list "/")
) -}}
{{- $routes = append $routes (dict
    "name" "accounts-api"
    "serviceName" (printf "%s-accounts" $releaseName)
    "port" (index .Values "accounts" "service" "port")
    "paths" (list "/api/users" "/api/accounts")
) -}}
{{- $routes = append $routes (dict
    "name" "cash-api"
    "serviceName" (printf "%s-cash" $releaseName)
    "port" (index .Values "cash" "service" "port")
    "paths" (list "/api/cash")
) -}}
{{- $routes = append $routes (dict
    "name" "transfer-api"
    "serviceName" (printf "%s-transfer" $releaseName)
    "port" (index .Values "transfer" "service" "port")
    "paths" (list "/api/transfer")
) -}}
{{- $routes = append $routes (dict
    "name" "exchange-api"
    "serviceName" (printf "%s-exchange" $releaseName)
    "port" (index .Values "exchange" "service" "port")
    "paths" (list "/api/exchange")
) -}}
{{- $routes = append $routes (dict
    "name" "notifications-api"
    "serviceName" (printf "%s-notifications" $releaseName)
    "port" (index .Values "notifications" "service" "port")
    "paths" (list "/api/notifications")
) -}}
{{- $routes = append $routes (dict
    "name" "blocker-api"
    "serviceName" (printf "%s-blocker" $releaseName)
    "port" (index .Values "blocker" "service" "port")
    "paths" (list "/api/blocker")
) -}}
{{- toJson $routes -}}
{{- end -}}


