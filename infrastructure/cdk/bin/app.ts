#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { AiInfluencerStack } from '../lib/ai-influencer-stack';

const app = new cdk.App();

new AiInfluencerStack(app, 'AiInfluencerStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: 'ap-southeast-2' // Sydney region
  },
  description: 'AI Influencer Agent - Production Infrastructure',
  tags: {
    Project: 'AIInfluencer',
    Environment: 'Production',
    ManagedBy: 'CDK'
  }
});
