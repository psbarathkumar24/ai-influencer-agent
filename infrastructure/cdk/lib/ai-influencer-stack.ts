import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

export class AiInfluencerStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // VPC with public and private subnets across 2 AZs
    const vpc = new ec2.Vpc(this, 'AiInfluencerVPC', {
      maxAzs: 2,
      natGateways: 1,
      subnetConfiguration: [
        {
          cidrMask: 24,
          name: 'Public',
          subnetType: ec2.SubnetType.PUBLIC,
        },
        {
          cidrMask: 24,
          name: 'Private',
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        },
        {
          cidrMask: 28,
          name: 'Database',
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
        },
      ],
    });

    // S3 Bucket for video storage
    const videoBucket = new s3.Bucket(this, 'VideoBucket', {
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      versioned: true,
      lifecycleRules: [
        {
          id: 'DeleteOldVersions',
          noncurrentVersionExpiration: cdk.Duration.days(30),
        },
      ],
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // Secrets Manager for sensitive credentials
    const dbCredentials = new secretsmanager.Secret(this, 'DBCredentials', {
      secretName: 'ai-influencer/db-credentials',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'aiinfluencer' }),
        generateStringKey: 'password',
        excludePunctuation: true,
        includeSpace: false,
        passwordLength: 32,
      },
    });

    const apiKeys = new secretsmanager.Secret(this, 'APIKeys', {
      secretName: 'ai-influencer/api-keys',
      secretObjectValue: {
        openaiApiKey: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
        elevenlabsApiKey: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
        elevenlabsVoiceId: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
        videoRendererWebhookSecret: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
        jwtSecret: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
      },
    });

    const oauthCredentials = new secretsmanager.Secret(this, 'OAuthCredentials', {
      secretName: 'ai-influencer/oauth-credentials',
      secretObjectValue: {
        googleClientId: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
        googleClientSecret: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
        twitterClientId: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
        twitterClientSecret: cdk.SecretValue.unsafePlainText('PLACEHOLDER'),
      },
    });

    // RDS PostgreSQL Database
    const dbSecurityGroup = new ec2.SecurityGroup(this, 'DBSecurityGroup', {
      vpc,
      description: 'Security group for RDS database',
      allowAllOutbound: false,
    });

    const database = new rds.DatabaseInstance(this, 'Database', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16,
      }),
      instanceType: ec2.InstanceType.of(
        ec2.InstanceClass.T4G,
        ec2.InstanceSize.SMALL
      ),
      vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
      },
      securityGroups: [dbSecurityGroup],
      credentials: rds.Credentials.fromSecret(dbCredentials),
      databaseName: 'aiinfluencer',
      allocatedStorage: 20,
      maxAllocatedStorage: 100,
      storageEncrypted: true,
      backupRetention: cdk.Duration.days(7),
      deleteAutomatedBackups: true,
      removalPolicy: cdk.RemovalPolicy.SNAPSHOT,
      deletionProtection: true,
      multiAz: false, // Set to true for production HA
    });

    // ECS Cluster
    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc,
      clusterName: 'ai-influencer-cluster',
      containerInsights: true,
    });

    // CloudWatch Log Group
    const logGroup = new logs.LogGroup(this, 'AppLogGroup', {
      logGroupName: '/ecs/ai-influencer',
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Task Execution Role
    const taskExecutionRole = new iam.Role(this, 'TaskExecutionRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName(
          'service-role/AmazonECSTaskExecutionRolePolicy'
        ),
      ],
    });

    // Grant access to secrets
    dbCredentials.grantRead(taskExecutionRole);
    apiKeys.grantRead(taskExecutionRole);
    oauthCredentials.grantRead(taskExecutionRole);

    // Task Role (for application permissions)
    const taskRole = new iam.Role(this, 'TaskRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

    // Grant S3 access to task role
    videoBucket.grantReadWrite(taskRole);

    // Grant Secrets Manager access
    apiKeys.grantRead(taskRole);
    oauthCredentials.grantRead(taskRole);

    // Fargate Task Definition
    const taskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      memoryLimitMiB: 2048,
      cpu: 1024,
      executionRole: taskExecutionRole,
      taskRole: taskRole,
    });

    // Container Definition
    const container = taskDefinition.addContainer('AppContainer', {
      image: ecs.ContainerImage.fromRegistry('ai-influencer-backend:latest'),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: 'app',
        logGroup: logGroup,
      }),
      environment: {
        AWS_REGION: this.region,
        AWS_S3_BUCKET: videoBucket.bucketName,
        DATABASE_URL: `jdbc:postgresql://${database.dbInstanceEndpointAddress}:5432/aiinfluencer`,
      },
      secrets: {
        DATABASE_USERNAME: ecs.Secret.fromSecretsManager(dbCredentials, 'username'),
        DATABASE_PASSWORD: ecs.Secret.fromSecretsManager(dbCredentials, 'password'),
        OPENAI_API_KEY: ecs.Secret.fromSecretsManager(apiKeys, 'openaiApiKey'),
        ELEVENLABS_API_KEY: ecs.Secret.fromSecretsManager(apiKeys, 'elevenlabsApiKey'),
        ELEVENLABS_VOICE_ID: ecs.Secret.fromSecretsManager(apiKeys, 'elevenlabsVoiceId'),
        VIDEO_RENDERER_WEBHOOK_SECRET: ecs.Secret.fromSecretsManager(
          apiKeys,
          'videoRendererWebhookSecret'
        ),
        JWT_SECRET: ecs.Secret.fromSecretsManager(apiKeys, 'jwtSecret'),
        GOOGLE_CLIENT_ID: ecs.Secret.fromSecretsManager(
          oauthCredentials,
          'googleClientId'
        ),
        GOOGLE_CLIENT_SECRET: ecs.Secret.fromSecretsManager(
          oauthCredentials,
          'googleClientSecret'
        ),
        TWITTER_CLIENT_ID: ecs.Secret.fromSecretsManager(
          oauthCredentials,
          'twitterClientId'
        ),
        TWITTER_CLIENT_SECRET: ecs.Secret.fromSecretsManager(
          oauthCredentials,
          'twitterClientSecret'
        ),
      },
      healthCheck: {
        command: [
          'CMD-SHELL',
          'curl -f http://localhost:8080/actuator/health || exit 1',
        ],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        startPeriod: cdk.Duration.seconds(60),
      },
    });

    container.addPortMappings({
      containerPort: 8080,
      protocol: ecs.Protocol.TCP,
    });

    // Application Load Balancer
    const alb = new elbv2.ApplicationLoadBalancer(this, 'ALB', {
      vpc,
      internetFacing: true,
      loadBalancerName: 'ai-influencer-alb',
    });

    const listener = alb.addListener('PublicListener', {
      port: 80,
      open: true,
    });

    // ECS Service Security Group
    const serviceSecurityGroup = new ec2.SecurityGroup(this, 'ServiceSecurityGroup', {
      vpc,
      description: 'Security group for ECS service',
    });

    // Allow traffic from ALB to ECS service
    serviceSecurityGroup.addIngressRule(
      ec2.Peer.securityGroupId(alb.connections.securityGroups[0].securityGroupId),
      ec2.Port.tcp(8080),
      'Allow inbound from ALB'
    );

    // Allow ECS service to access database
    dbSecurityGroup.addIngressRule(
      serviceSecurityGroup,
      ec2.Port.tcp(5432),
      'Allow from ECS service'
    );

    // Fargate Service
    const service = new ecs.FargateService(this, 'Service', {
      cluster,
      taskDefinition,
      desiredCount: 2,
      serviceName: 'ai-influencer-service',
      securityGroups: [serviceSecurityGroup],
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
      healthCheckGracePeriod: cdk.Duration.seconds(60),
      circuitBreaker: { rollback: true },
    });

    // Attach service to ALB
    const targetGroup = listener.addTargets('ECS', {
      port: 8080,
      targets: [service],
      healthCheck: {
        path: '/actuator/health',
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      },
      deregistrationDelay: cdk.Duration.seconds(30),
    });

    // Auto Scaling
    const scaling = service.autoScaleTaskCount({
      minCapacity: 2,
      maxCapacity: 10,
    });

    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 70,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    scaling.scaleOnMemoryUtilization('MemoryScaling', {
      targetUtilizationPercent: 80,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    // Outputs
    new cdk.CfnOutput(this, 'LoadBalancerDNS', {
      value: alb.loadBalancerDnsName,
      description: 'Application Load Balancer DNS',
      exportName: 'AIInfluencerALBDNS',
    });

    new cdk.CfnOutput(this, 'S3BucketName', {
      value: videoBucket.bucketName,
      description: 'S3 Bucket for video storage',
    });

    new cdk.CfnOutput(this, 'DatabaseEndpoint', {
      value: database.dbInstanceEndpointAddress,
      description: 'RDS Database endpoint',
    });

    new cdk.CfnOutput(this, 'ClusterName', {
      value: cluster.clusterName,
      description: 'ECS Cluster name',
    });
  }
}
