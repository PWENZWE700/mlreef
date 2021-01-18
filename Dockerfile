####################################################################
####################################################################
# Welcome to MLReef Nautilus dockerfile.
#
# https://gitlab.com/gitlab-org/omnibus-gitlab/-/tree/master/docker
#
####################################################################
FROM registry.gitlab.com/mlreef/mlreef/gateway:master AS FRONTEND_MASTER
FROM registry.gitlab.com/mlreef/mlreef/backend:master AS BACKEND_MASTER
FROM gradle:6.5-jdk8 AS BACKEND_BUILDER

ENV JVM_OPTS -Xmx2g -Xms2g -XX:MaxPermSize=1024m

# provide a tmp/cache dir
VOLUME /tmp

# all following commands will be executed in /app
WORKDIR /workdir
# copy the sources to image (except .dockerignore)
ADD backend/ /workdir
RUN gradle -x test :mlreef-rest:bootJar :mlreef-rest:prepareDocker -x :mlreef-rest:asciidoctor
# Start a new docker stage here, and only copy the finished build artefacts.


FROM gitlab/gitlab-ce:12.7.0-ce.0 AS NAUTILUS
MAINTAINER mlreef.com

# Setup JAVA_HOME -- useful for docker commandline
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/
RUN export JAVA_HOME

# TODO rename to MLREEF_INSTANCE_HOST
ENV INSTANCE_HOST "localhost"
# TODO rename to MLREEF_GITLAB_PORT
ENV GITLAB_PORT "10080"
# TODO rename to MLREEF_GITLAB_ROOT_URL
ENV GITLAB_ROOT_URL "http://$INSTANCE_HOST:$GITLAB_PORT"
ENV GITLAB_OMNIBUS_CONFIG "\
    # This is the URL that Gitlab expects to be addressed at.   \
    # This URL will be sent to the runners as repo cloning url  \
    external_url '$GITLAB_ROOT_URL';                            \
    # Deactivate HTTPS redirection of Gitlab's API gateway      \
    nginx['redirect_http_to_https'] = false;                    \
    # The external URL for the internal Docker registry         \
    registry_external_url 'http://localhost:5050';              \
    registry_nginx['enable'] = true;                            \
    # Access port for the internal Docker registry              \
    # (has to be exposed via Docker as well)                    \
    registry_nginx['listen_port'] = 5050;                       \
    redis['bind'] = '127.0.0.1';                                \
    redis['port'] = 6379;                                       \
    "

# TODO is this correct, can it be moved in the above GITLAB_OMNIBUS_CONFIG block
ENV GITLAB_HTTPS "false"
# TODO is this correct, can it be moved in the above GITLAB_OMNIBUS_CONFIG block
ENV TZ 'Austria/Vienna'
# TODO is this correct, can it be moved in the above GITLAB_OMNIBUS_CONFIG block
ENV GITLAB_TIMEZONE 'Vienna'
# TODO is this correct, can it be moved in the above GITLAB_OMNIBUS_CONFIG block
ENV SSL_SELF_SIGNED 'false'

# MLReef Confguration
# Postgres version for MLReef backend Database
ENV MLREEF_PG_VERSION "11"
# Postgres OS and super user
ENV MLREEF_PG_USER "postgres"
# Postgres cluster name
ENV MLREEF_PG_CLUSTER "mlreefdb"
# Postgres mlreef log directory
ENV MLREEF_PG_LOG "/var/log/${MLREEF_PG_CLUSTER}-postgresql"
# TODO rename to MLREEF_DB_EXTENSION
ENV DB_EXTENSION "pg_trgm"
### Backend Config
ENV MLREEF_BACKEND_PORT "8081"
#Select the Backend's Spring profile
# TODO rename to MLREEF_SPRING_PROFILES_ACTIVE
ENV SPRING_PROFILES_ACTIVE "docker"
# Backend DB host
# TODO rename to MLREEF_DB_HOST
ENV DB_HOST "localhost"
# Backend DB port
# TODO rename to MLREEF_DB_PORT
ENV DB_PORT "6000"
# Backend DB user
# TODO rename to MLREEF_DB_USER
ENV DB_USER "mlreef"
# Backend DB password
# TODO rename to MLREEF_DB_PASS
ENV DB_PASS "password"
# Backend DB name
# TODO rename to MLREEF_DB_NAME
ENV DB_NAME "mlreef_backend"
# Backend Redis host
# TODO rename to MLREEF_REDIS_HOST
ENV REDIS_HOST "localhost"
# Backend Startup delay
# TODO rename to MLREEF_STARTUP_DELAY
ENV STARTUP_DELAY "30"



###
### Modify Gitlab Omnibus scripts
###
RUN mv /assets/wrapper /assets/gitlab-wrapper
# Remove the wait for sigterm from the gitlab wrapper script to make it "interactive"
# The MLReef wrapper will handle starting and stopping of services
RUN sed -i "/# Tail all logs/d" /assets/gitlab-wrapper
RUN sed -i "/# gitlab-ctl tail &/d" /assets/gitlab-wrapper
RUN sed -i "/# Wait for SIGTERM/d" /assets/gitlab-wrapper
RUN sed -i "/wait/d" /assets/gitlab-wrapper



###
### GITLAB RUNNER
###
# Install Gitlab Runner in Docker container
# https://docs.gitlab.com/runner/install/linux-manually.html
RUN apt-get update                          && \
    curl -L https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh | bash  && \
    apt-get install --yes gitlab-runner     && \
    apt-get clean                           && \
    gitlab-runner --version



###
### SOFTWARE
###
# Install NGINX reverse proxy
# Install Open JDK 8 and fix cert issues
RUN apt-get update               && \
    apt-get install --assume-yes    \
    # NGINX is MLReef's API gateway
    nginx                           \
    ant                             \
    # THe java runtime for the MLReef backend
    openjdk-8-jdk                   \
    ca-certificates-java         && \
    # cleanup apt-get cache
    apt-get clean                && \
    # Fix cert issues
    update-ca-certificates -f    && \
    # Remove nginx default config
    rm -rf /etc/nginx/sites-enabled/default



####
#### BACKEND DATABASE
####
# Set up PostgreSQL for mlreefdb
# Add postgres repo to apt package manager
RUN apt-get update                                                                          && \
    apt-get install -y wget vim lsb-release                                                 && \
    wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc |  apt-key add -   && \
    RELEASE=$(lsb_release -cs)                                                              && \
    echo "deb http://apt.postgresql.org/pub/repos/apt/ ${RELEASE}"-pgdg main |  tee  /etc/apt/sources.list.d/pgdg.list

# Install Postgres
RUN apt-get update                      && \
    apt-get install -y acl sudo locales    \
    postgresql-${MLREEF_PG_VERSION} postgresql-client-${MLREEF_PG_VERSION} postgresql-contrib-${MLREEF_PG_VERSION}

# Basic config changes to hba and postgresql
RUN mkdir -p ${MLREEF_PG_LOG}         && \
    pg_createcluster ${MLREEF_PG_VERSION} ${MLREEF_PG_CLUSTER} -d /var/opt/mlreef/${MLREEF_PG_VERSION}/${MLREEF_PG_CLUSTER} \
                     -p ${DB_PORT} -l ${MLREEF_PG_LOG}/postgresql-${MLREEF_PG_VERSION}-${MLREEF_PG_CLUSTER}.log                     && \
    echo "host all all 0.0.0.0/0 md5" >> /etc/postgresql/${MLREEF_PG_VERSION}/${MLREEF_PG_CLUSTER}/pg_hba.conf                      && \
    sed -i  "s/^#listen_addresses.*=.*'localhost'/listen_addresses='*'/g" /etc/postgresql/${MLREEF_PG_VERSION}/${MLREEF_PG_CLUSTER}/postgresql.conf                                 && \
    cat /etc/postgresql/${MLREEF_PG_VERSION}/${MLREEF_PG_CLUSTER}/postgresql.conf

# Default DB, DB user and extension creation
RUN pg_ctlcluster ${MLREEF_PG_VERSION} ${MLREEF_PG_CLUSTER} start                                                           && \
    ps -ef | grep postgres                                                                                                  && \
    su -c - ${MLREEF_PG_USER} "psql -p ${DB_PORT} --command \"CREATE USER $DB_USER WITH SUPERUSER PASSWORD '$DB_PASS';\""   && \
    su -c - ${MLREEF_PG_USER} "createdb -p ${DB_PORT} -O ${DB_USER} ${DB_NAME}"                                             && \
    su -c - ${MLREEF_PG_USER} "psql -p ${DB_PORT} ${DB_NAME} --command \"CREATE EXTENSION IF NOT EXISTS ${DB_EXTENSION};\"" && \
    pg_ctlcluster ${MLREEF_PG_VERSION} ${MLREEF_PG_CLUSTER} stop



######
###### BACKEND
######
# either from master:
# COPY --from=BACKEND_MASTER /app /app
# or: from BACKEND_BUILDER
# add the gradle dependencies and own artificats in a docker-friendly way
COPY --from=BACKEND_BUILDER /workdir/mlreef-rest/build/dependency/BOOT-INF/classes /app
COPY --from=BACKEND_BUILDER /workdir/mlreef-rest/build/dependency/BOOT-INF/lib     /app/lib
COPY --from=BACKEND_BUILDER /workdir/mlreef-rest/build/dependency/META-INF         /app/META-INF



######
###### FRONTEND
######
# Add nginx configuration. Note the name change of the file
COPY --from=FRONTEND_MASTER /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
RUN chmod 777 /etc/nginx/conf.d/default.conf
# Copy frontend production build from the NPM stage
# This path has to correspond to the configuration in nginx_default.conf
COPY --from=FRONTEND_MASTER /usr/share/nginx/html /usr/share/nginx/html
# Copy the test coverage report to the final stage
# The CI pipeline later extracts this report and makes it available in Gitlab
COPY --from=FRONTEND_MASTER /usr/share/coverage /usr/share/coverage

# TODO FIXME: nginx: [emerg] "gzip" directive is duplicate in /etc/nginx/conf.d/default.conf:14
RUN sed -i "/gzip on;/d" /etc/nginx/conf.d/default.conf
# Adapt the NGINX rules to work on localhost
RUN sed -i "s/gitlab:10080/localhost:10080/" /etc/nginx/conf.d/default.conf
RUN sed -i "s/backend:8080/localhost:8081/" /etc/nginx/conf.d/default.conf


# Wrapper to handle additional script to run after default gitlab image's /assets/wrapper
ADD nautilus/assets/ /assets
CMD ["/assets/mlreef-wrapper"]

# Volumes from Gitlab base image
VOLUME ["/etc/gitlab", "/var/log/gitlab","/var/opt/gitlab"]
# Volumes for mlreef's backend database
VOLUME  ["/etc/postgresql", "/var/log/mlreef-postgresql", "/var/opt/mlreef"]

# Expose mlreef postgres
EXPOSE $DB_PORT
# Expose HTTPS ports
EXPOSE $MLREEF_BACKEND_PORT 80 443 10080
# Expose Gitlab SSH port
EXPOSE 22
# Expose Docker registry port
EXPOSE 5050

ENV GITLAB_ROOT_EMAIL    "roo@localhost"
ENV GITLAB_ROOT_PASSWORD "password"
ENV GITLAB_ADMIN_TOKEN   "token"    # The GITLAB_ADMIN_TOKEN is shared between Gitlab and the Backend

# These secrets are used by Gitlab to encrypt passwords and tokens
# Changing them will invalidate the GITLAB_ADMIN_TOKEN as well as all other tokens
ENV GITLAB_SECRETS_SECRET_KEY_BASE  "secret1111111111122222222222333333333334444444444555555555566666666661234"
ENV GITLAB_SECRETS_OTP_KEY_BASE     "secret1111111111122222222222333333333334444444444555555555566666666661234"
ENV GITLAB_SECRETS_DB_KEY_BASE      "secret1111111111122222222222333333333334444444444555555555566666666661234"
