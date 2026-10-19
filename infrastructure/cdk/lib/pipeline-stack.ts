import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { CodePipeline, CodePipelineSource, ShellStep } from 'aws-cdk-lib/pipelines';

export class MyPipelineStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const pipeline = new CodePipeline(this, 'Pipeline', {
      pipelineName: 'AIAgentDeploymentPipeline',
      synth: new ShellStep('Synth', {
        // 1. Link your Git Repo directly using the connection ARN from Step 1
        input: CodePipelineSource.connection('/psbarathkumar24/ai-influencer-agent', 'main', {
          connectionArn: 'arn:aws:codeconnections:ap-southeast-2:736308618086:connection/42d098f0-6263-4b10-b176-717f91e13a59',
        }),
        // 2. Define the build/synth commands for your CDK environment
        commands: [
          'npm ci',          // Installs dependencies from your project lockfile
          'npm run build',   // Compiles your TS code
          'npx cdk synth'    // Synthesizes into a CloudFormation template
        ],
      }),
    });
  }
}
